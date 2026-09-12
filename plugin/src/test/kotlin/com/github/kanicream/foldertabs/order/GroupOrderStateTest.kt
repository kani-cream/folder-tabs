package com.github.kanicream.foldertabs.order

import org.junit.Assert.assertEquals
import org.junit.Test

/** Design section 7.1: persisted group order is never trusted; state round-trips through the XML form. */
class GroupOrderStateTest {

    @Test
    fun `loadState drops blanks and duplicates and caps the list`() {
        val state = GroupOrderState()
        val kept = listOf("file:///a") + (1..GroupOrder.DEFAULT_CAP + 5).map { "file:///d$it" }
        state.loadState(GroupOrderState.State().apply { urls = (listOf("", " ", "file:///a") + kept).toMutableList() })
        assertEquals(kept.take(GroupOrder.DEFAULT_CAP), state.savedUrls)
    }

    @Test
    fun `getState mirrors the saved order as a fresh list`() {
        val state = GroupOrderState()
        state.update { listOf("file:///b", "file:///a") }
        val xml = state.getState()
        assertEquals(listOf("file:///b", "file:///a"), xml.urls)
        xml.urls.clear()
        assertEquals("the component's own list is untouched", listOf("file:///b", "file:///a"), state.savedUrls)
    }

    @Test
    fun `update replaces the list immutably and caps it`() {
        val state = GroupOrderState()
        state.update { listOf("file:///x") }
        val before = state.savedUrls
        state.update { (1..GroupOrder.DEFAULT_CAP + 1).map { "file:///d$it" } }
        assertEquals("the list handed out earlier is untouched", listOf("file:///x"), before)
        assertEquals(GroupOrder.DEFAULT_CAP, state.savedUrls.size)
    }
}
