package com.github.kanicream.foldertabs.actions

import com.github.kanicream.foldertabs.service.GroupedTabsProjectService
import com.github.kanicream.foldertabs.settings.FolderTabsSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

/**
 * `Window > Editor Tabs > Collapse Folder Tabs` (design section 4.1.3, issue #26): checked while
 * the project's headers are collapsed to their one-line bar. Per project, runtime only; the
 * Settings page's master switch is untouched. Also offered in every tab's right-click menu, and
 * assignable in Settings > Keymap (no default shortcut). Stable Public API Only.
 */
class ToggleCollapsedAction : DumbAwareToggleAction() {

    // The header registry is EDT-only state (design section 18).
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = service(e)?.headersCollapsed ?: false

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        service(e)?.setHeadersCollapsed(state)
    }

    /** Nothing to collapse while the Settings master switch is off: the headers are not there. */
    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = service(e) != null && FolderTabsSettings.getInstance().enabled
    }

    private fun service(e: AnActionEvent): GroupedTabsProjectService? = GroupedTabsProjectService.getInstanceOrNull(e.project)

    companion object {
        const val ID: String = "FolderTabs.ToggleCollapsed"
    }
}
