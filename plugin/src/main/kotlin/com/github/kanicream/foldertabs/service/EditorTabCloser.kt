package com.github.kanicream.foldertabs.service

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Closes one file tab the way the IDE does (design section 15).
 *
 * The IDE's own `CloseEditor` action closes the `VIRTUAL_FILE` of its context in the editor window
 * found in that context, so with a context taken from inside the header (which lives in the
 * window's tab container) and the clicked file laid over it, the file is closed in exactly that
 * split pane — without touching `EditorWindow` / `FileEditorManagerEx` ourselves. When the context
 * carries no window (the action would then close the *active* file of the current window
 * instead), the project-wide [FileEditorManager.closeFile] is used.
 */
class EditorTabCloser(
    private val project: Project,
    private val perform: (AnAction, AnActionEvent) -> Unit = { action, event -> ActionUtil.performAction(action, event) },
    private val hasEditorWindow: (DataContext) -> Boolean = { it.getData(EDITOR_WINDOW) != null },
) {

    fun close(file: VirtualFile, headerContext: DataContext) {
        val manager = FileEditorManager.getInstance(project)
        if (!file.isValid || !manager.isFileOpen(file)) return
        val action = ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_EDITOR)
        if (action == null || !hasEditorWindow(headerContext)) {
            manager.closeFile(file)
            return
        }
        val context = CustomizedDataContext.withSnapshot(headerContext) { sink ->
            sink[CommonDataKeys.VIRTUAL_FILE] = file
        }
        val event = AnActionEvent.createEvent(
            action, context, action.templatePresentation.clone(), ActionPlaces.EDITOR_TAB, ActionUiKind.NONE, null,
        )
        perform(action, event)
    }

    companion object {
        /**
         * Name of the platform's editor-window data key (`EditorWindow.DATA_KEY`). [DataKey.create]
         * returns the one instance per name, so this resolves the same value the IDE's close action
         * reads, while the window type itself stays unreferenced (Stable Public API Only, section 2.1).
         */
        val EDITOR_WINDOW: DataKey<Any> = DataKey.create("editorWindow")
    }
}
