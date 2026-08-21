package com.github.kanicream.foldertabs.model

import com.intellij.openapi.vfs.VirtualFile

/**
 * Immutable snapshot of the grouped open files (design section 17). The UI is a projection
 * of this model; per-editor selection state is supplied by the header that renders it.
 */
data class GroupedTabsModel(
    val groups: List<DirectoryGroupModel>,
) {
    fun groupOf(file: VirtualFile?): DirectoryGroupModel? =
        file?.let { f -> groups.firstOrNull { g -> g.files.any { it.file == f } } }

    /** Copy with the modified flag of [file] set to [modified] (design section 19, targeted update). */
    fun withModified(file: VirtualFile, modified: Boolean): GroupedTabsModel = GroupedTabsModel(
        groups.map { g -> g.copy(files = g.files.map { if (it.file == file) it.copy(modified = modified) else it }) },
    )

    companion object {
        val EMPTY = GroupedTabsModel(emptyList())
    }
}

data class DirectoryGroupModel(
    /** `null` for the fallback "Other" group (files without a parent). */
    val directory: VirtualFile?,
    val displayName: String,
    val fullPath: String,
    val files: List<FileTabModel>,
) {
    /**
     * Key under which the user-defined group order is persisted (design section 7.1): the
     * directory URL, or [OTHER_ORDER_KEY] for the "Other" group so it can be reordered too.
     */
    val orderKey: String get() = directory?.url ?: OTHER_ORDER_KEY

    companion object {
        /** Synthetic order key of the "Other" group; not a valid VFS URL by design. */
        const val OTHER_ORDER_KEY: String = "folder-tabs://other"
    }
}

data class FileTabModel(
    val file: VirtualFile,
    val displayName: String,
    val fullPath: String,
    val modified: Boolean,
)
