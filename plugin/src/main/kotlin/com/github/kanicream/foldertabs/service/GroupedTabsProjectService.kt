package com.github.kanicream.foldertabs.service

import com.github.kanicream.foldertabs.editor.EditorHeaderRegistry
import com.github.kanicream.foldertabs.grouping.DirectoryGroupBuilder
import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.github.kanicream.foldertabs.model.GroupedTabsModel
import com.github.kanicream.foldertabs.settings.FolderTabsSettings
import com.github.kanicream.foldertabs.ui.FolderTabsNavigator
import com.github.kanicream.foldertabs.ui.GroupedTabsPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile

/**
 * Project-level hub (design section 18): owns the model, the last-active tracker and the
 * editor-header registry; rebuilds the model from the open files and pushes it to every
 * header. All methods run on the EDT.
 */
@Service(Service.Level.PROJECT)
class GroupedTabsProjectService(private val project: Project) : Disposable, FolderTabsNavigator {

    private val log = Logger.getInstance(GroupedTabsProjectService::class.java)

    private val registry = EditorHeaderRegistry()
    private val lastActive = LastActiveFileTracker()

    @Volatile
    var model: GroupedTabsModel = GroupedTabsModel.EMPTY
        private set

    private var refreshPending = false

    val headerCount: Int get() = registry.size

    // ---- events from FolderTabsEditorListener ----------------------------------------

    fun onFileOpened(file: VirtualFile) {
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
        newFile?.let(::attachHeaders)
        requestRefresh()
    }

    /** Safety net for editors restored before the listener saw them. */
    fun attachAllOpenEditors() {
        editorManager().openFiles.forEach(::attachHeaders)
        requestRefresh()
    }

    // ---- FolderTabsNavigator -----------------------------------------------------------

    override fun openFile(file: VirtualFile) {
        if (!file.isValid) return
        editorManager().openFile(file, true)
    }

    override fun openGroup(group: DirectoryGroupModel) {
        lastActive.targetFor(group)?.let(::openFile)
    }

    // ---- refresh ----------------------------------------------------------------------

    /** Coalesces bursts of events into one rebuild per EDT turn (design section 19). */
    fun requestRefresh() {
        if (refreshPending) return
        refreshPending = true
        ApplicationManager.getApplication().invokeLater({
            refreshPending = false
            refreshNow()
        }, project.disposed)
    }

    fun refreshNow() {
        if (project.isDisposed) return
        pruneStaleHeaders()
        val policy = FolderTabsSettings.getInstance().labelPolicy(project.name)
        model = DirectoryGroupBuilder(project.basePath, policy).build(editorManager().openFiles.toList())
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
