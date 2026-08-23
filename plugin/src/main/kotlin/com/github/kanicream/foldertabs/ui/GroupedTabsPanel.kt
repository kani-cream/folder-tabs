package com.github.kanicream.foldertabs.ui

import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.github.kanicream.foldertabs.model.FileTabModel
import com.github.kanicream.foldertabs.model.GroupedTabsModel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBPanel
import com.intellij.util.IconUtil
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent

/**
 * The two-row header added above one [com.intellij.openapi.fileEditor.FileEditor]
 * (design sections 4, 11): Directory Group Tabs on top, File Tabs of the active group below.
 *
 * A header belongs to exactly one file ([ownFile]): the editor it sits on always shows that
 * file, so the header's active group/file are derived from it, not from global selection.
 * The header is a pure projection of the [GroupedTabsModel] handed to [render].
 */
class GroupedTabsPanel(
    private val project: Project,
    private val ownFile: VirtualFile,
    private val navigator: FolderTabsNavigator,
) : Disposable {

    private val groupTabs = TabStrip(project, this, onSelect = ::onGroupSelected, onReorder = ::onGroupsReordered)
    private val fileTabs = TabStrip(project, this, onSelect = ::onFileSelected, onClose = ::onFileClose)

    val component: JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(groupTabs.component, BorderLayout.NORTH)
        add(fileTabs.component, BorderLayout.SOUTH)
    }

    /** Currently rendered group of [ownFile]; null until the first render finds it. */
    var activeGroup: DirectoryGroupModel? = null
        private set

    fun render(model: GroupedTabsModel) {
        val group = model.groupOf(ownFile)
        activeGroup = group

        groupTabs.render(
            items = model.groups.map { TabStrip.Item(key = it, text = it.displayName, tooltip = it.fullPath, icon = AllIcons.Nodes.Folder) },
            selectedKey = group,
        )
        fileTabs.render(
            items = group?.files.orEmpty().map { TabStrip.Item(key = it.file, text = fileText(it), tooltip = it.fullPath, icon = fileIcon(it.file)) },
            selectedKey = ownFile,
        )
        // Tabs were added after the header joined the editor: make the editor re-layout.
        component.revalidate()
        component.repaint()
    }

    /** Targeted update for the modified indicator (design section 19): no strip rebuild. */
    fun updateModified(file: VirtualFile, modified: Boolean) {
        val tab = activeGroup?.files?.firstOrNull { it.file == file } ?: return
        fileTabs.updateText(file, fileText(tab.copy(modified = modified)))
    }

    /** Same icon the standard editor tabs show: file type plus read-status overlays, resolved lazily. */
    private fun fileIcon(file: VirtualFile): Icon? =
        if (file.isValid) IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, project) else null

    private fun fileText(tab: FileTabModel): String =
        if (tab.modified) MODIFIED_PREFIX + tab.displayName else tab.displayName

    private fun onGroupSelected(key: Any) {
        val group = key as? DirectoryGroupModel ?: return
        if (group == activeGroup) return
        navigator.openGroup(group)
    }

    private fun onGroupsReordered(keys: List<Any>) {
        navigator.reorderGroups(keys.filterIsInstance<DirectoryGroupModel>())
    }

    private fun onFileSelected(key: Any) {
        val file = key as? VirtualFile ?: return
        if (file == ownFile) return
        navigator.openFile(file)
    }

    private fun onFileClose(key: Any, context: DataContext) {
        val file = key as? VirtualFile ?: return
        navigator.closeFile(file, context)
    }

    override fun dispose() {
        // TabStrips are registered as children of this Disposable; nothing else to release.
    }

    companion object {
        /** Same convention as the IDE's "Mark modified (*)" editor-tab option (design section 4.1.2). */
        const val MODIFIED_PREFIX: String = "*"
    }
}
