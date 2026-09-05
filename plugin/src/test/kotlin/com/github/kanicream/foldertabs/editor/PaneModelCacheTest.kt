package com.github.kanicream.foldertabs.editor

import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.github.kanicream.foldertabs.model.FileTabModel
import com.github.kanicream.foldertabs.model.GroupedTabsModel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Design section 13.0: one model per pane from the IDE's own per-window file list; unknown pane = project-wide model. */
class PaneModelCacheTest {

    private val a = LightVirtualFile("a.txt")
    private val b = LightVirtualFile("b.txt")

    private fun modelOf(files: List<VirtualFile>) = GroupedTabsModel(
        listOf(DirectoryGroupModel(null, "Other", "Other", files.map { FileTabModel(it, it.name, it.name, modified = false) })),
    )

    private val full = modelOf(listOf(a, b))

    @Test
    fun `an unknown pane gets the project-wide model`() {
        val cache = PaneModelCache(full, paneFiles = { listOf(a) }, build = ::modelOf)
        assertSame(full, cache.modelFor(null))
    }

    @Test
    fun `a known pane gets a model built from that pane's files`() {
        val cache = PaneModelCache(full, paneFiles = { pane -> if (pane == "L") listOf(a) else listOf(b) }, build = ::modelOf)
        assertEquals(listOf(a), cache.modelFor("L").groups.single().files.map { it.file })
        assertEquals(listOf(b), cache.modelFor("R").groups.single().files.map { it.file })
    }

    @Test
    fun `a pane the IDE does not list falls back to the project-wide model`() {
        val cache = PaneModelCache(full, paneFiles = { null }, build = ::modelOf)
        assertSame(full, cache.modelFor("gone"))
    }

    @Test
    fun `headers of the same pane share one model built once`() {
        var builds = 0
        val cache = PaneModelCache(full, paneFiles = { listOf(a) }, build = { builds++; modelOf(it) })
        val first = cache.modelFor("L")
        assertSame(first, cache.modelFor("L"))
        assertEquals(1, builds)
    }
}
