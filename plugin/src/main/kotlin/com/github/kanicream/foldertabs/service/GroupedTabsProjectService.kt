package com.github.kanicream.foldertabs.service

import com.github.kanicream.foldertabs.editor.EditorHeaderRegistry
import com.github.kanicream.foldertabs.grouping.DirectoryGroupBuilder
import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.github.kanicream.foldertabs.model.GroupedTabsModel
import com.github.kanicream.foldertabs.order.GroupOrder
import com.github.kanicream.foldertabs.order.GroupOrderState
import com.github.kanicream.foldertabs.settings.FolderTabsSettings
import com.github.kanicream.foldertabs.ui.FolderTabsNavigator
import com.github.kanicream.foldertabs.ui.GroupedTabsPanel
import com.github.kanicream.foldertabs.vfs.VfsChangeClassifier
import com.github.kanicream.foldertabs.vfs.VfsChangeSummary
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Project-level hub (design section 18): owns the model, the last-active tracker and the
 * editor-header registry; rebuilds the model from the open files and pushes it to every
 * header. Mutating methods run on the EDT; [requestRefresh] may be called from any thread.
 */
@Service(Service.Level.PROJECT)
class GroupedTabsProjectService(private val project: Project) : Disposable, FolderTabsNavigator {

    private val log = Logger.getInstance(GroupedTabsProjectService::class.java)

    private val registry = EditorHeaderRegistry()
    private val lastActive = LastActiveFileTracker()
    private val refreshPending = AtomicBoolean(false)

    @Volatile
    var model: GroupedTabsModel = GroupedTabsModel.EMPTY
        private set

    /** Number of full rebuilds so far; tests use it to prove unrelated events do not rebuild. */
    @Volatile
    var rebuildCount: Int = 0
        private set

    val headerCount: Int get() = registry.size

    init {
        // VFS_CHANGES is an application topic broadcast to project buses; the connection
        // dies with this service (design sections 10, 20).
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) = onVfsEvents(events)
        })
        // Typing toggles the modified flag; update only the affected tab (design section 19).
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                FileDocumentManager.getInstance().getFile(event.document)?.let(::onModifiedStateMayHaveChanged)
            }
        }, this)
    }

    // ---- events from FolderTabsEditorListener ----------------------------------------

    private val enabled: Boolean get() = FolderTabsSettings.getInstance().enabled

    fun onFileOpened(file: VirtualFile) {
        if (!enabled) return
        attachHeaders(file)
        requestRefresh()
    }

    fun onFileClosed(file: VirtualFile) {
        // Editors of a closed file are released through their Disposable hook (see
        // attachHeader) and by pruneStaleHeaders(); do not rely on event ordering here.
        if (editorManager().getAllEditors(file).isEmpty()) lastActive.forget(file)
        requestRefresh()
    }

    fun onSelectionChanged(newFile: VirtualFile?) {
        newFile?.let(lastActive::remember)
        if (!enabled) return
        newFile?.let(::attachHeaders)
        requestRefresh()
    }

    /** Safety net for editors restored before the listener saw them. */
    fun attachAllOpenEditors() {
        if (!enabled) return
        editorManager().openFiles.forEach(::attachHeaders)
        requestRefresh()
    }

    /** Re-applies Application-level settings (design section 21): ON attaches, OFF removes all headers. */
    fun applySettings() {
        if (enabled) {
            attachAllOpenEditors()
        } else {
            registry.all().forEach { (editor, _) -> detachHeader(editor) }
        }
    }

    // ---- VFS (design section 10) ------------------------------------------------------

    fun onVfsEvents(events: List<VFileEvent>) {
        if (!enabled) return
        val summary: VfsChangeSummary = runCatching {
            VfsChangeClassifier.classify(events, editorManager().openFiles.toList())
        }.getOrElse {
            log.warn("Folder Tabs: could not classify VFS events", it)
            return
        }
        if (summary.isEmpty) return

        val order = GroupOrderState.getInstance(project)
        summary.renamedUrls.forEach { (old, new) -> order.update { GroupOrder.rename(it, old, new) } }
        summary.deletedUrls.forEach { url -> order.update { GroupOrder.remove(it, url) } }

        if (summary.structureChanged) requestRefresh()
        summary.contentChangedFiles.forEach(::onModifiedStateMayHaveChanged)
    }

    // ---- modified indicator (design section 4.1.2 / 19) -------------------------------

    private fun onModifiedStateMayHaveChanged(file: VirtualFile) {
        val shown = model.groupOf(file)?.files?.firstOrNull { it.file == file }?.modified ?: return
        val actual = FileDocumentManager.getInstance().isFileModified(file)
        if (shown == actual) return
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            model = model.withModified(file, actual)
            registry.all().forEach { (_, panel) -> panel.updateModified(file, actual) }
        }, project.disposed)
    }

    // ---- FolderTabsNavigator -----------------------------------------------------------

    override fun openFile(file: VirtualFile) {
        if (!file.isValid) return
        editorManager().openFile(file, true)
    }

    override fun openGroup(group: DirectoryGroupModel) {
        lastActive.targetFor(group)?.let(::openFile)
    }

    override fun reorderGroups(groupsInNewOrder: List<DirectoryGroupModel>) {
        val urls = groupsInNewOrder.mapNotNull { it.directory?.url }
        if (urls.isEmpty()) return
        GroupOrderState.getInstance(project).update { GroupOrder.applyReorder(it, urls) }
        requestRefresh()
    }

    // ---- refresh ----------------------------------------------------------------------

    /** Coalesces bursts of events into one rebuild per EDT turn (design section 19). */
    fun requestRefresh() {
        if (!refreshPending.compareAndSet(false, true)) return
        ApplicationManager.getApplication().invokeLater({
            refreshPending.set(false)
            refreshNow()
        }, project.disposed)
    }

    fun refreshNow() {
        if (project.isDisposed) return
        pruneStaleHeaders()
        val policy = FolderTabsSettings.getInstance().labelPolicy(project.name)
        val order = GroupOrderState.getInstance(project).savedUrls
        model = DirectoryGroupBuilder(project.basePath, policy, order).build(editorManager().openFiles.toList())
        rebuildCount++
        registry.all().forEach { (editor, panel) ->
            runCatching { panel.render(model) }
                .onFailure { log.warn("Folder Tabs: header render failed for ${editor.file}", it) }
        }
    }

    // ---- headers ----------------------------------------------------------------------

    private fun attachHeaders(file: VirtualFile) {
        editorManager().getAllEditors(file)
            .filter { !registry.contains(it) }
            .forEach { attachHeader(it, file) }
    }

    private fun attachHeader(editor: FileEditor, file: VirtualFile) {
        val panel = GroupedTabsPanel(project, file, this)
        Disposer.register(this, panel)
        runCatching {
            editorManager().addTopComponent(editor, panel.component)
            registry.register(editor, panel)
            // Release the header when the platform disposes the editor (design section 11.2).
            Disposer.register(editor, Disposable { detachHeader(editor) })
            panel.render(model)
        }.onFailure {
            // Fail-safe: the standard editor keeps working without the header (design section 23).
            log.warn("Folder Tabs: could not attach header to editor for $file", it)
            registry.unregister(editor)
            runCatching { editorManager().removeTopComponent(editor, panel.component) }
            Disposer.dispose(panel)
        }
    }

    /** Drops headers whose editor the platform no longer lists (safety net for missed disposals). */
    private fun pruneStaleHeaders() {
        val live = editorManager().allEditors.toSet()
        registry.all()
            .map { (editor, _) -> editor }
            .filter { it !in live }
            .forEach(::detachHeader)
    }

    private fun detachHeader(editor: FileEditor) {
        val panel = registry.unregister(editor) ?: return
        runCatching { editorManager().removeTopComponent(editor, panel.component) }
            .onFailure { log.debug("Folder Tabs: removeTopComponent failed (editor already gone?)", it) }
        Disposer.dispose(panel)
    }

    private fun editorManager(): FileEditorManager = FileEditorManager.getInstance(project)

    override fun dispose() {
        registry.all().forEach { (editor, _) -> detachHeader(editor) }
    }

    companion object {
        fun getInstance(project: Project): GroupedTabsProjectService =
            project.getService(GroupedTabsProjectService::class.java)
    }
}
