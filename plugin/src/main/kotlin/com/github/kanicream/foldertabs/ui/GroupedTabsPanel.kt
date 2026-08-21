package com.github.kanicream.foldertabs.ui

import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.github.kanicream.foldertabs.model.GroupedTabsModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
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
    project: Project,
    private val ownFile: VirtualFile,
    private val navigator: FolderTabsNavigator,
) : Disposable {

    private val groupTabs = TabStrip(project, this, onSelect = ::onGroupSelected)
    private val fileTabs = TabStrip(project, this, onSelect = ::onFileSelected)

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
            items = model.groups.map { TabStrip.Item(key = it, text = it.displayName, tooltip = it.fullPath) },
            selectedKey = group,
        )
        fileTabs.render(
            items = group?.files.orEmpty().map { TabStrip.Item(key = it.file, text = it.displayName, tooltip = it.fullPath) },
            selectedKey = ownFile,
        )
        // Tabs were added after the header joined the editor: make the editor re-layout.
        component.revalidate()
        component.repaint()
    }

    private fun onGroupSelected(key: Any) {
        val group = key as? DirectoryGroupModel ?: return
        if (group == activeGroup) return
        navigator.openGroup(group)
    }

    private fun onFileSelected(key: Any) {
        val file = key as? VirtualFile ?: return
        if (file == ownFile) return
        navigator.openFile(file)
    }

    override fun dispose() {
        // TabStrips are registered as children of this Disposable; nothing else to release.
    }
}
