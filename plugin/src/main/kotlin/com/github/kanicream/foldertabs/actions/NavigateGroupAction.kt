package com.github.kanicream.foldertabs.actions

import com.github.kanicream.foldertabs.model.GroupDirection
import com.github.kanicream.foldertabs.service.GroupedTabsProjectService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction

/**
 * Moves to the adjacent directory group of the invoking editor's header and opens that group's
 * last active file, exactly like a click on the group tab (design section 8.4, issue #24).
 *
 * The target editor is the one in the action's data context, provided it carries a header;
 * otherwise (a tool window, the main menu, an editor hosted outside the editor area) the last
 * focused editor is used, as the platform's own Next Tab does. No default shortcut is shipped:
 * users assign one in Settings > Keymap. Stable Public API Only: `DumbAwareAction`,
 * `PlatformDataKeys.FILE_EDITOR`, `FileEditorManager.selectedEditor`.
 */
abstract class NavigateGroupAction(private val direction: GroupDirection) : DumbAwareAction() {

    // The header registry is EDT-only state (design section 18).
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val target = targetOf(e)
        e.presentation.isEnabled = target != null && target.service.canNavigateGroups(target.editor)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val target = targetOf(e) ?: return
        target.service.navigateGroup(target.editor, direction)
    }

    private fun targetOf(e: AnActionEvent): Target? {
        val project = e.project ?: return null
        if (project.isDisposed) return null
        val service = GroupedTabsProjectService.getInstance(project)
        val editor = listOfNotNull(e.getData(PlatformDataKeys.FILE_EDITOR), FileEditorManager.getInstance(project).selectedEditor)
            .firstOrNull(service::hasHeader)
            ?: return null
        return Target(service, editor)
    }

    private data class Target(val service: GroupedTabsProjectService, val editor: FileEditor)
}

/** `Window > Editor Tabs > Next Folder Group`; text and description come from the bundle. */
class NextGroupAction : NavigateGroupAction(GroupDirection.NEXT) {
    companion object {
        const val ID: String = "FolderTabs.NextGroup"
    }
}

/** `Window > Editor Tabs > Previous Folder Group`; text and description come from the bundle. */
class PreviousGroupAction : NavigateGroupAction(GroupDirection.PREVIOUS) {
    companion object {
        const val ID: String = "FolderTabs.PreviousGroup"
    }
}
