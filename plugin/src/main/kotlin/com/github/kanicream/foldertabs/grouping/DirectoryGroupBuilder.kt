package com.github.kanicream.foldertabs.grouping

import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.github.kanicream.foldertabs.model.FileTabModel
import com.github.kanicream.foldertabs.model.GroupedTabsModel
import com.github.kanicream.foldertabs.order.GroupOrder
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Builds the [GroupedTabsModel] from the currently open files (design sections 5, 6.5, 7, 9):
 * one group per immediate parent directory, no recursion, deterministic natural order.
 * User-defined orders (sections 7.1, 7.2) are applied on top of that default order.
 *
 * Files that are invalid or directories are dropped; files without a parent fall into the
 * "Other" group (design section 14).
 */
class DirectoryGroupBuilder(
    private val projectBasePath: String?,
    private val labelPolicy: GroupLabelPolicy,
    /** User-defined group order, [DirectoryGroupModel.orderKey]s leftmost first (design section 7.1). */
    private val savedGroupOrder: List<String> = emptyList(),
    /** User-defined file order of a group, file URLs leftmost first, by [DirectoryGroupModel.orderKey] (section 7.2). */
    private val savedFileOrder: (orderKey: String) -> List<String>? = { null },
    private val isModified: (VirtualFile) -> Boolean = { FileDocumentManager.getInstance().isFileModified(it) },
) {

    fun build(openFiles: Collection<VirtualFile>): GroupedTabsModel {
        val eligible = openFiles.filter { it.isValid && !it.isDirectory }.distinct()
        if (eligible.isEmpty()) return GroupedTabsModel.EMPTY

        val byParent: Map<VirtualFile?, List<VirtualFile>> = eligible.groupBy { it.parent }
        val displayNames = resolveDisplayNames(byParent.keys)

        val groups = byParent.map { (directory, files) ->
            val group = DirectoryGroupModel(
                directory = directory,
                displayName = displayNames[directory] ?: MinimalUniquePathResolver.FALLBACK_NAME,
                fullPath = directory?.presentableUrl ?: MinimalUniquePathResolver.FALLBACK_NAME,
                files = files.map(::toFileTab).sortedWith(FILE_ORDER),
            )
            group.copy(files = applySavedFileOrder(group))
        }.sortedWith(GROUP_ORDER)

        return GroupedTabsModel(GroupOrder.sort(groups, { it.orderKey }, savedGroupOrder))
    }

    private fun resolveDisplayNames(directories: Set<VirtualFile?>): Map<VirtualFile?, String> {
        val sources = directories.associateWith { dir ->
            dir?.let { DirectoryPathSegments.of(it.path, projectBasePath) }
                ?: DirectoryLabelSource(emptyList(), projectRelative = false)
        }
        return MinimalUniquePathResolver.resolve(sources, labelPolicy)
    }

    private fun applySavedFileOrder(group: DirectoryGroupModel): List<FileTabModel> {
        val saved = savedFileOrder(group.orderKey).orEmpty()
        if (saved.isEmpty()) return group.files
        return GroupOrder.sort(group.files, { it.file.url }, saved)
    }

    private fun toFileTab(file: VirtualFile) = FileTabModel(
        file = file,
        displayName = file.presentableName,
        fullPath = file.presentableUrl,
        modified = isModified(file),
    )

    private companion object {
        val GROUP_ORDER: Comparator<DirectoryGroupModel> =
            compareBy<DirectoryGroupModel, String>(NaturalOrderComparator) { it.displayName }
                .thenBy { it.fullPath }

        val FILE_ORDER: Comparator<FileTabModel> =
            compareBy<FileTabModel, String>(NaturalOrderComparator) { it.displayName }
                .thenBy { it.fullPath }
    }
}
