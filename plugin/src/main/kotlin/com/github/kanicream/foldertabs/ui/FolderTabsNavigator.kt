package com.github.kanicream.foldertabs.ui

import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.intellij.openapi.vfs.VirtualFile

/** What a header can ask the service to do (design sections 7.1, 8). Keeps the UI free of IDE calls. */
interface FolderTabsNavigator {
    fun openFile(file: VirtualFile)
    fun openGroup(group: DirectoryGroupModel)

    /** The user dragged group tabs into [groupsInNewOrder] (leftmost first). */
    fun reorderGroups(groupsInNewOrder: List<DirectoryGroupModel>)
}
