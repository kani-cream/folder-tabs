package com.github.kanicream.foldertabs

import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.github.kanicream.foldertabs.ui.FolderTabsNavigator
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent

/** A navigator that does nothing; tests override only what they assert on. */
open class NoopFolderTabsNavigator : FolderTabsNavigator {
    override fun openFile(file: VirtualFile, pane: JComponent?) = Unit
    override fun openGroup(group: DirectoryGroupModel, pane: JComponent?) = Unit
    override fun closeFile(file: VirtualFile, headerContext: DataContext) = Unit
    override fun closeGroup(group: DirectoryGroupModel, headerContext: DataContext) = Unit
    override fun reorderGroups(groupsInNewOrder: List<DirectoryGroupModel>) = Unit
    override fun reorderFiles(group: DirectoryGroupModel, filesInNewOrder: List<VirtualFile>) = Unit
    override fun setHeadersCollapsed(collapsed: Boolean) = Unit
}
