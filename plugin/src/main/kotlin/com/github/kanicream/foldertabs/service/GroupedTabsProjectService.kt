package com.github.kanicream.foldertabs.service

import com.github.kanicream.foldertabs.editor.EditorHeaderRegistry
import com.github.kanicream.foldertabs.editor.PaneModelCache
import com.github.kanicream.foldertabs.grouping.DirectoryGroupBuilder
import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.github.kanicream.foldertabs.model.GroupedTabsModel
import com.github.kanicream.foldertabs.order.FileOrder
import com.github.kanicream.foldertabs.order.FileOrderState
import com.github.kanicream.foldertabs.order.GroupOrder
import com.github.kanicream.foldertabs.order.GroupOrderState
import com.github.kanicream.foldertabs.settings.FolderTabsSettings
import com.github.kanicream.foldertabs.ui.FolderTabsNavigator
import com.github.kanicream.foldertabs.ui.GroupedTabsPanel
import com.github.kanicream.foldertabs.vfs.VfsChangeClassifier
import com.github.kanicream.foldertabs.vfs.VfsChangeSummary
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.util.ui.UIUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent

/**
 * Project-level hub (design section 18): owns the model, the last-active trackers and the
 * editor-header registry; rebuilds the model from the open files and pushes it to every
 * header. Mutating methods run on the EDT; [requestRefresh] may be called from any thread.
 *
 * Split panes (design section 13.0): [model] stays project-wide, but every header renders the
 * projection for its own pane. A header's pane is resolved whenever the header joins a window,
 * through the `editorWindow` data key the platform's tab container publishes into the data
 * context of everything inside that pane; the pane's files are then read from the IDE's own
 * window list ([paneFiles]). Both are used read-only (design section 2.1, v1.3 exception).
 */
@Service(Service.Level.PROJECT)
class GroupedTabsProjectService(private val project: Project) : Disposable, FolderTabsNavigator {

    private val log = Logger.getInstance(GroupedTabsProjectService::class.java)

    private val registry = EditorHeaderRegistry()

    /** Last active file per group, per pane; the `null` pane is the project-wide fallback. */
    private var lastActiveByPane: Map<Any?, LastActiveFileTracker> = mapOf(null to LastActiveFileTracker())

    /** Resolves the split pane behind a header (editor + header component); `null` = unknown. */
    private var paneResolver: (FileEditor, JComponent) -> Any? = { _, header ->
        DataManager.getInstance().getDataContext(header).getData(EditorTabCloser.EDITOR_WINDOW)
    }

    /**
     * The files the IDE lists in [pane], in its tab order; `null` when the IDE no longer lists
     * that pane. The pane is matched by identity against the IDE's windows: the only place the
     * window type is named (design section 2.1, v1.3 exception: `getWindows` / `getFileList`).
     */
    private var paneFiles: (pane: Any) -> List<VirtualFile>? = { pane ->
        FileEditorManagerEx.getInstanceEx(project).windows.firstOrNull { it === pane }?.fileList
    }
    private val closer = EditorTabCloser(project)
    private val opener = EditorPaneOpener(project)
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
        if (editorManager().getAllEditors(file).isEmpty()) lastActiveByPane.values.forEach { it.forget(file) }
        requestRefresh()
    }

    fun onSelectionChanged(newFile: VirtualFile?, newEditor: FileEditor? = null) {
        if (newFile != null) rememberLastActive(newFile, newEditor)
        if (!enabled) return
        newFile?.let(::attachHeaders)
        requestRefresh()
    }

    private fun rememberLastActive(file: VirtualFile, editor: FileEditor?) {
        val pane = editor?.let { paneOf(it) }
        trackerFor(null).remember(file)
        if (pane != null) trackerFor(pane).remember(file)
    }

    private fun trackerFor(pane: Any?): LastActiveFileTracker =
        lastActiveByPane[pane] ?: LastActiveFileTracker().also { lastActiveByPane = lastActiveByPane + (pane to it) }

    /** The pane of [editor]: the attributed one, else resolved from its component right now. */
    private fun paneOf(editor: FileEditor): Any? =
        registry.paneOf(editor) ?: runCatching { paneResolver(editor, editor.component) }.getOrNull()

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

        val groupOrder = GroupOrderState.getInstance(project)
        val fileOrder = FileOrderState.getInstance(project)
        summary.renamedUrls.forEach { (old, new) ->
            groupOrder.update { GroupOrder.rename(it, old, new) }
            fileOrder.update { FileOrder.rename(it, old, new) }
        }
        summary.deletedUrls.forEach { url ->
            groupOrder.update { GroupOrder.remove(it, url) }
            fileOrder.update { FileOrder.remove(it, url) }
        }

        if (summary.structureChanged) requestRefresh()
        summary.contentChangedFiles.forEach(::onModifiedStateMayHaveChanged)
    }

    // ---- modified indicator (design section 4.1.2 / 19) -------------------------------

    private fun onModifiedStateMayHaveChanged(file: VirtualFile) {
        val shown = model.groupOf(file)?.files?.firstOrNull { it.file == file }?.modified ?: return
        if (shown == FileDocumentManager.getInstance().isFileModified(file)) return
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            // Re-read both sides here: the state seen when the event fired can be stale by the
            // time this runs (an edit and a save in the same EDT turn), and applying it would
            // leave a wrong modified marker until the next event.
            val current = model.groupOf(file)?.files?.firstOrNull { it.file == file }?.modified ?: return@invokeLater
            val actual = FileDocumentManager.getInstance().isFileModified(file)
            if (current == actual) return@invokeLater
            model = model.withModified(file, actual)
            registry.all().forEach { (_, panel) -> panel.updateModified(file, actual) }
        }, project.disposed)
    }

    // ---- FolderTabsNavigator -----------------------------------------------------------

    override fun openFile(file: VirtualFile, pane: JComponent?) = opener.open(file, pane)

    override fun openGroup(group: DirectoryGroupModel, pane: JComponent?) {
        val paneKey = pane?.let(registry::editorOwning)?.let(registry::paneOf)
        val target = trackerFor(paneKey).targetFor(group) ?: trackerFor(null).targetFor(group)
        target?.let { openFile(it, pane) }
    }

    override fun closeFile(file: VirtualFile, headerContext: DataContext) = closer.close(file, headerContext)

    override fun closeGroup(group: DirectoryGroupModel, headerContext: DataContext) =
        closer.closeAll(group.files.map { it.file }, headerContext)

    override fun reorderGroups(groupsInNewOrder: List<DirectoryGroupModel>) {
        val keys = groupsInNewOrder.map { it.orderKey }
        if (keys.isEmpty()) return
        GroupOrderState.getInstance(project).update { GroupOrder.applyReorder(it, keys) }
        requestRefresh()
    }

    override fun reorderFiles(group: DirectoryGroupModel, filesInNewOrder: List<VirtualFile>) {
        val urls = filesInNewOrder.map { it.url }
        if (urls.isEmpty()) return
        FileOrderState.getInstance(project).update { FileOrder.applyReorder(it, group.orderKey, urls) }
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
        val groupOrder = GroupOrderState.getInstance(project).savedUrls
        val fileOrder = FileOrderState.getInstance(project).saved
        val builder = DirectoryGroupBuilder(
            project.basePath, policy, groupOrder,
            savedFileOrder = { key -> FileOrder.savedFor(fileOrder, key) },
        )
        val openFiles = editorManager().openFiles.toList()
        model = builder.build(openFiles)
        rebuildCount++
        // Every header renders its own pane's projection (design section 13.0); same pane, same model.
        val paneModels = PaneModelCache(model, ::filesOfPane, builder::build)
        registry.all().forEach { (editor, panel) ->
            runCatching { panel.render(paneModels.modelFor(registry.paneOf(editor))) }
                .onFailure { log.warn("Folder Tabs: header render failed for ${editor.file}", it) }
        }
    }

    /** Fail-safe (design section 23): a listing failure falls back to the project-wide model. */
    private fun filesOfPane(pane: Any): List<VirtualFile>? =
        runCatching { paneFiles(pane) }
            .onFailure { log.warn("Folder Tabs: could not list the files of a split pane", it) }
            .getOrNull()

    // ---- headers ----------------------------------------------------------------------

    private fun attachHeaders(file: VirtualFile) {
        editorManager().getAllEditors(file)
            .filter { !registry.contains(it) }
            .forEach { attachHeader(it, file) }
    }

    private fun attachHeader(editor: FileEditor, file: VirtualFile) {
        // Same rule as the platform's EditorTabs: the header is "active" while its editor owns the focus.
        val panel = GroupedTabsPanel(
            project, file, this,
            isEditorActive = { UIUtil.isFocusAncestor(editor.component) },
            editorFocusTarget = { editor.preferredFocusedComponent ?: editor.component },
            onShown = { onHeaderShown(editor) },
        )
        Disposer.register(this, panel)
        runCatching {
            editorManager().addTopComponent(editor, panel.component)
            registry.register(editor, panel)
            // Release the header when the platform disposes the editor (design section 11.2).
            Disposer.register(editor, Disposable { detachHeader(editor) })
            panel.render(model)
            if (panel.component.isShowing) onHeaderShown(editor)
        }.onFailure {
            // Fail-safe: the standard editor keeps working without the header (design section 23).
            log.warn("Folder Tabs: could not attach header to editor for $file", it)
            registry.unregister(editor)
            runCatching { editorManager().removeTopComponent(editor, panel.component) }
            Disposer.dispose(panel)
        }
    }

    /**
     * The header of [editor] joined a window: (re)resolve its split pane (design section 13). Tabs
     * can move between splits, so the pane is re-resolved on every show; a change re-renders.
     */
    private fun onHeaderShown(editor: FileEditor) {
        val panel = registry.panelOf(editor) ?: return
        val pane = runCatching { paneResolver(editor, panel.component) }
            .onFailure { log.debug("Folder Tabs: pane resolution failed for ${editor.file}", it) }
            .getOrNull() ?: return
        if (registry.paneOf(editor) == pane) return
        registry.attribute(editor, pane)
        requestRefresh()
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

    // ---- test hooks ----

    /** Test hook: replaces the pane resolver (a light test has no split windows). */
    internal var paneResolverForTest: (FileEditor, JComponent) -> Any?
        get() = paneResolver
        set(value) { paneResolver = value }

    /** Test hook: replaces the IDE's per-pane file listing. */
    internal var paneFilesForTest: (Any) -> List<VirtualFile>?
        get() = paneFiles
        set(value) { paneFiles = value }

    /** Test hook: what the header's `addNotify` triggers. */
    internal fun headerShownForTest(editor: FileEditor) = onHeaderShown(editor)

    /** Test hook: the header attached to [editor]. */
    internal fun panelForTest(editor: FileEditor): GroupedTabsPanel = registry.panelOf(editor)!!

    companion object {
        fun getInstance(project: Project): GroupedTabsProjectService =
            project.getService(GroupedTabsProjectService::class.java)
    }
}
