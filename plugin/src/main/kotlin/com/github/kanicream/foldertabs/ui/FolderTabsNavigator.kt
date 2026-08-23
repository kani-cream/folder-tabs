package com.github.kanicream.foldertabs.ui

import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.vfs.VirtualFile

/** What a header can ask the service to do (design sections 7.1, 8). Keeps the UI free of IDE calls. */
interface FolderTabsNavigator {
    fun openFile(file: VirtualFile)
    fun openGroup(group: DirectoryGroupModel)

    /** Close [file] the way the IDE would from a tab inside [headerContext] (design section 15). */
    fun closeFile(file: VirtualFile, headerContext: DataContext)

    /** Close every file of [group], each the way [closeFile] does (design section 15.1). */
    fun closeGroup(group: DirectoryGroupModel, headerContext: DataContext)

    /** The user dragged group tabs into [groupsInNewOrder] (leftmost first). */
    fun reorderGroups(groupsInNewOrder: List<DirectoryGroupModel>)
}
