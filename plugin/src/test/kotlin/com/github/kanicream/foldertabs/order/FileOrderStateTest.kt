package com.github.kanicream.foldertabs.order

import org.junit.Assert.assertEquals
import org.junit.Test

/** Design section 7.2: persisted input is never trusted; state round-trips through the XML form. */
class FileOrderStateTest {

    @Test
    fun `loadState drops blanks and duplicates and empty directories`() {
        val state = FileOrderState()
        state.loadState(
            FileOrderState.State().apply {
                entries = mutableListOf(
                    FileOrderState.Entry().apply { directory = "d1"; files = mutableListOf("a", "", "a", "b") },
                    FileOrderState.Entry().apply { directory = ""; files = mutableListOf("x") },
                    FileOrderState.Entry().apply { directory = "d2"; files = mutableListOf(" ") },
                )
            },
        )
        assertEquals(mapOf("d1" to listOf("a", "b")), state.saved)
    }

    @Test
    fun `getState mirrors the saved map`() {
        val state = FileOrderState()
        state.update { FileOrder.applyReorder(it, "d1", listOf("b", "a")) }
        val xml = state.getState()
        assertEquals(listOf("d1"), xml.entries.map { it.directory })
        assertEquals(listOf("b", "a"), xml.entries.single().files)
    }

    @Test
    fun `update replaces the map immutably`() {
        val state = FileOrderState()
        val before = state.saved
        state.update { FileOrder.applyReorder(it, "d1", listOf("a")) }
        assertEquals(emptyMap<String, List<String>>(), before)
        assertEquals(mapOf("d1" to listOf("a")), state.saved)
    }
}
