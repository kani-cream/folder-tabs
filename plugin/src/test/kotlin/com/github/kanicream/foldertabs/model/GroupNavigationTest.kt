package com.github.kanicream.foldertabs.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Design section 8.4 / issue #24: the adjacent group wraps around at both ends. */
class GroupNavigationTest {

    private val keys = listOf("api", "orders", "users")

    @Test
    fun `next moves to the following group`() {
        assertEquals("orders", GroupNavigation.adjacent(keys, "api", GroupDirection.NEXT))
    }

    @Test
    fun `previous moves to the preceding group`() {
        assertEquals("api", GroupNavigation.adjacent(keys, "orders", GroupDirection.PREVIOUS))
    }

    @Test
    fun `next wraps from the last group to the first`() {
        assertEquals("api", GroupNavigation.adjacent(keys, "users", GroupDirection.NEXT))
    }

    @Test
    fun `previous wraps from the first group to the last`() {
        assertEquals("users", GroupNavigation.adjacent(keys, "api", GroupDirection.PREVIOUS))
    }

    @Test
    fun `a single group has no neighbour`() {
        assertNull(GroupNavigation.adjacent(listOf("api"), "api", GroupDirection.NEXT))
        assertNull(GroupNavigation.adjacent(listOf("api"), "api", GroupDirection.PREVIOUS))
    }

    @Test
    fun `no groups means nowhere to go`() {
        assertNull(GroupNavigation.adjacent(emptyList<String>(), null, GroupDirection.NEXT))
    }

    @Test
    fun `an unknown current group has no neighbour`() {
        assertNull(GroupNavigation.adjacent(keys, null, GroupDirection.NEXT))
        assertNull(GroupNavigation.adjacent(keys, "gone", GroupDirection.PREVIOUS))
    }
}
