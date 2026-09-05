package com.github.kanicream.foldertabs.editor

import com.github.kanicream.foldertabs.model.GroupedTabsModel
import com.intellij.openapi.vfs.VirtualFile

/**
 * One [GroupedTabsModel] per split pane for a single refresh (design section 13, v1.3): the
 * pane's file subset comes from [PanePartition], the model from [build]; headers of the same pane
 * share one instance, and the unknown pane (`null`) gets the project-wide [fullModel].
 */
class PaneModelCache(
    private val openFiles: List<VirtualFile>,
    private val filesByPane: Map<Any, Set<VirtualFile>>,
    fullModel: GroupedTabsModel,
    private val build: (List<VirtualFile>) -> GroupedTabsModel,
) {
    private var built: Map<Any?, GroupedTabsModel> = mapOf(null to fullModel)

    fun modelFor(pane: Any?): GroupedTabsModel =
        built[pane] ?: build(PanePartition.filesFor(pane, openFiles, filesByPane)).also { built = built + (pane to it) }
}
