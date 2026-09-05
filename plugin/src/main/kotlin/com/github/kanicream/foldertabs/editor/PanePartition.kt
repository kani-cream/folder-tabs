package com.github.kanicream.foldertabs.editor

import com.intellij.openapi.vfs.VirtualFile

/**
 * Which open files a split pane's header shows (design section 13, v1.3).
 *
 * A pane shows the files whose editors were attributed to it, plus every open file that is not
 * attributed to any pane at all: such a file was opened but never shown (session restore, opened
 * in the background), so its pane is unknown and hiding it would lose it. A header whose own pane
 * is unknown ([pane] `null`) shows everything, exactly like the pre-v1.3 shared model.
 */
object PanePartition {

    fun <F : VirtualFile> filesFor(pane: Any?, openFiles: List<F>, filesByPane: Map<Any, Set<VirtualFile>>): List<F> {
        if (pane == null) return openFiles
        val own = filesByPane[pane].orEmpty()
        val attributedSomewhere = filesByPane.values.flatten().toSet()
        return openFiles.filter { it in own || it !in attributedSomewhere }
    }
}
