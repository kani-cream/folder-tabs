package com.github.kanicream.foldertabs.editor

import com.intellij.testFramework.LightVirtualFile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Design section 13 (v1.3): each pane shows the files attributed to it, plus every open file that
 * is not attributed to any pane yet (never shown since it was opened), so nothing is ever hidden.
 */
class PanePartitionTest {

    private val a = LightVirtualFile("a.txt")
    private val b = LightVirtualFile("b.txt")
    private val c = LightVirtualFile("c.txt")
    private val open = listOf(a, b, c)

    @Test
    fun `a pane gets its own files and the unattributed ones, in open-files order`() {
        val byPane = mapOf<Any, Set<LightVirtualFile>>("L" to setOf(a), "R" to setOf(b))
        assertEquals(listOf(a, c), PanePartition.filesFor("L", open, byPane))
        assertEquals(listOf(b, c), PanePartition.filesFor("R", open, byPane))
    }

    @Test
    fun `a file open in two panes appears in both`() {
        val byPane = mapOf<Any, Set<LightVirtualFile>>("L" to setOf(a, b), "R" to setOf(b))
        assertEquals(listOf(a, b, c), PanePartition.filesFor("L", open, byPane))
        assertEquals(listOf(b, c), PanePartition.filesFor("R", open, byPane))
    }

    @Test
    fun `an unknown pane sees every open file`() {
        val byPane = mapOf<Any, Set<LightVirtualFile>>("L" to setOf(a))
        assertEquals(open, PanePartition.filesFor(null, open, byPane))
        assertEquals(listOf(b, c), PanePartition.filesFor("never-seen", open, byPane))
    }

    @Test
    fun `with no attribution at all every pane sees every open file`() {
        assertEquals(open, PanePartition.filesFor("L", open, emptyMap()))
    }

    @Test
    fun `attributed files that are no longer open are ignored`() {
        val byPane = mapOf<Any, Set<LightVirtualFile>>("L" to setOf(a, LightVirtualFile("gone.txt")))
        assertEquals(listOf(a, b, c), PanePartition.filesFor("L", open, byPane))
    }
}
