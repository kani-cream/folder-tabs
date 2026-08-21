package com.github.kanicream.foldertabs.settings

import com.github.kanicream.foldertabs.FolderTabsBundle
import com.github.kanicream.foldertabs.grouping.GroupLabelPolicy
import com.github.kanicream.foldertabs.service.GroupedTabsProjectService
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

/** `Settings > Tools > Folder Tabs` (design section 21). Application-level. */
class FolderTabsConfigurable : BoundSearchableConfigurable(
    FolderTabsBundle.message("settings.display.name"),
    HELP_TOPIC,
    ID,
) {

    override fun createPanel(): DialogPanel {
        val settings = FolderTabsSettings.getInstance()
        return panel {
            row {
                checkBox(FolderTabsBundle.message("settings.enabled"))
                    .bindSelected({ settings.enabled }, { settings.enabled = it })
                    .comment(FolderTabsBundle.message("settings.enabled.comment"))
            }
            row(FolderTabsBundle.message("settings.group.label.depth")) {
                comboBox(FolderTabsSettings.DEPTH_CHOICES, DepthRenderer())
                    .bindItem({ settings.groupLabelDepth }, { settings.groupLabelDepth = it ?: GroupLabelPolicy.DEFAULT_DEPTH })
                    .comment(FolderTabsBundle.message("settings.group.label.depth.comment"))
            }
        }
    }

    override fun apply() {
        super.apply()
        // Settings are application-wide: apply to every open project (design 6.5 / 21).
        ProjectManager.getInstance().openProjects
            .filter { !it.isDisposed }
            .forEach { GroupedTabsProjectService.getInstance(it).applySettings() }
    }

    private class DepthRenderer : SimpleListCellRenderer<Int>() {
        override fun customize(list: javax.swing.JList<out Int>, value: Int?, index: Int, selected: Boolean, hasFocus: Boolean) {
            text = when (value) {
                null -> ""
                GroupLabelPolicy.PROJECT_ROOT_DEPTH -> FolderTabsBundle.message("settings.group.label.depth.project.root")
                else -> value.toString()
            }
        }
    }

    companion object {
        const val ID: String = "com.github.kanicream.foldertabs.settings"
        const val HELP_TOPIC: String = "folder.tabs.settings"
    }
}
