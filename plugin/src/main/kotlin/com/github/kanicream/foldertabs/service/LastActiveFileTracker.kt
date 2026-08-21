package com.github.kanicream.foldertabs.service

import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.intellij.openapi.vfs.VirtualFile

/**
 * Remembers the last selected file per directory so that re-selecting a group restores it
 * (design section 8.2 / 8.3). Runtime state only; not persisted in v1.0.
 *
 * Thread-safety: written and read on the EDT only.
 */
class LastActiveFileTracker {

    private var lastActiveByDirectory: Map<VirtualFile?, VirtualFile> = emptyMap()

    fun remember(file: VirtualFile) {
        lastActiveByDirectory = lastActiveByDirectory + (file.parent to file)
    }

    /**
     * Target for a group click: the last active file of that group if it is still open,
     * otherwise the first file in sort order (design section 8.2).
     */
    fun targetFor(group: DirectoryGroupModel): VirtualFile? {
        val remembered = lastActiveByDirectory[group.directory]
        if (remembered != null && group.files.any { it.file == remembered }) return remembered
        return group.files.firstOrNull()?.file
    }

    fun forget(file: VirtualFile) {
        lastActiveByDirectory = lastActiveByDirectory.filterValues { it != file }
    }
}
