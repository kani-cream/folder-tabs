package com.github.kanicream.foldertabs.editor

import com.github.kanicream.foldertabs.model.GroupedTabsModel
import com.intellij.openapi.vfs.VirtualFile

/**
 * One [GroupedTabsModel] per split pane for a single refresh (design section 13.0): a pane's
 * files come from [paneFiles] (the IDE's own per-window tab list), the model from [build].
 * Headers of the same pane share one instance. The unknown pane (`null`), and a pane the IDE
 * no longer lists, get the project-wide [fullModel] so a header never goes empty by mistake.
 */
class PaneModelCache(
    private val fullModel: GroupedTabsModel,
    private val paneFiles: (pane: Any) -> List<VirtualFile>?,
    private val build: (List<VirtualFile>) -> GroupedTabsModel,
) {
    private var built: Map<Any?, GroupedTabsModel> = mapOf(null to fullModel)

    fun modelFor(pane: Any?): GroupedTabsModel = built[pane] ?: create(pane).also { built = built + (pane to it) }

    private fun create(pane: Any?): GroupedTabsModel {
        val files = pane?.let(paneFiles) ?: return fullModel
        return build(files)
    }
}
