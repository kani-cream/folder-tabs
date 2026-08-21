package com.github.kanicream.foldertabs.vfs

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

/** What a batch of VFS events means for Folder Tabs (design sections 10 and 19). */
data class VfsChangeSummary(
    /** rename / move / delete touching an open file or one of its ancestors. */
    val structureChanged: Boolean,
    /** Directory (or file) URL rewrites to apply to the saved group order: old -> new. */
    val renamedUrls: List<Pair<String, String>>,
    /** URLs deleted; saved order entries under them are dropped. */
    val deletedUrls: List<String>,
    /** Open files whose content changed on disk (saved / reloaded): modified flag may have flipped. */
    val contentChangedFiles: List<VirtualFile>,
) {
    val isEmpty: Boolean
        get() = !structureChanged && renamedUrls.isEmpty() && deletedUrls.isEmpty() && contentChangedFiles.isEmpty()

    companion object {
        val NONE = VfsChangeSummary(false, emptyList(), emptyList(), emptyList())
    }
}

/**
 * Filters VFS events down to what affects the model, so Git checkouts and builds do not
 * trigger rebuilds (design section 10). Called from `BulkFileListener.after`.
 */
object VfsChangeClassifier {

    fun classify(events: List<VFileEvent>, openFiles: Collection<VirtualFile>): VfsChangeSummary {
        if (events.isEmpty() || openFiles.isEmpty()) return VfsChangeSummary.NONE
        val renamed = mutableListOf<Pair<String, String>>()
        val deleted = mutableListOf<String>()
        val contentChanged = mutableListOf<VirtualFile>()
        var structure = false

        for (event in events) {
            when (event) {
                is VFilePropertyChangeEvent -> if (event.isRename) {
                    val file = event.file
                    val parentUrl = file.parent?.url ?: continue
                    renamed += "$parentUrl/${event.oldValue}" to file.url
                    if (touchesOpenFile(file, openFiles)) structure = true
                }
                is VFileMoveEvent -> {
                    val file = event.file
                    renamed += "${event.oldParent.url}/${file.name}" to file.url
                    if (touchesOpenFile(file, openFiles)) structure = true
                }
                is VFileDeleteEvent -> {
                    deleted += event.file.url
                    if (touchesOpenFile(event.file, openFiles)) structure = true
                }
                is VFileContentChangeEvent -> {
                    if (event.file in openFiles) contentChanged += event.file
                }
                else -> Unit // create / copy / other property changes: not relevant
            }
        }
        return VfsChangeSummary(structure, renamed, deleted, contentChanged)
    }

    /** True when [changed] is an open file or an ancestor of one (its group label may change). */
    private fun touchesOpenFile(changed: VirtualFile, openFiles: Collection<VirtualFile>): Boolean =
        openFiles.any { open -> open == changed || VfsUtilCore.isAncestor(changed, open, true) }
}
