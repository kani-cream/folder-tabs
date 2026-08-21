package com.github.kanicream.foldertabs.order

import org.junit.Assert.assertEquals
import org.junit.Test

/** Design section 7.1: user order first, then default order; saved entries survive close/rename. */
class GroupOrderTest {

    @Test
    fun `saved entries come first in saved order, rest keep default order`() {
        val ordered = GroupOrder.sort(
            items = listOf("a", "b", "c", "d"),
            urlOf = { it },
            savedUrls = listOf("c", "a"),
        )
        assertEquals(listOf("c", "a", "b", "d"), ordered)
    }

    @Test
    fun `saved entries that are not open are ignored for sorting`() {
        val ordered = GroupOrder.sort(listOf("a", "b"), { it }, listOf("zzz", "b"))
        assertEquals(listOf("b", "a"), ordered)
    }

    @Test
    fun `reorder keeps hidden entries in their slots`() {
        // saved: x, a, y, b  (x and y are closed); user drags b before a
        val result = GroupOrder.applyReorder(saved = listOf("x", "a", "y", "b"), visibleNewOrder = listOf("b", "a"))
        assertEquals(listOf("x", "b", "y", "a"), result)
    }

    @Test
    fun `reorder appends newly seen groups after saved ones`() {
        val result = GroupOrder.applyReorder(saved = listOf("a"), visibleNewOrder = listOf("n2", "a", "n1"))
        assertEquals(listOf("a", "n2", "n1"), result)
    }

    @Test
    fun `reorder with empty saved list records the visible order`() {
        assertEquals(listOf("b", "a"), GroupOrder.applyReorder(emptyList(), listOf("b", "a")))
    }

    @Test
    fun `reorder drops oldest hidden entries beyond the cap`() {
        val saved = (1..5).map { "h$it" } + listOf("a")
        val result = GroupOrder.applyReorder(saved, listOf("a", "b"), cap = 4)
        assertEquals(4, result.size)
        assertEquals(listOf("h4", "h5", "a", "b"), result)
    }

    @Test
    fun `rename rewrites the entry and its descendants`() {
        val saved = listOf("file:///p/users", "file:///p/users/dto", "file:///p/usersX", "file:///p/orders")
        val result = GroupOrder.rename(saved, oldUrl = "file:///p/users", newUrl = "file:///p/accounts")
        assertEquals(
            listOf("file:///p/accounts", "file:///p/accounts/dto", "file:///p/usersX", "file:///p/orders"),
            result,
        )
    }

    @Test
    fun `remove drops the entry and its descendants`() {
        val saved = listOf("file:///p/users", "file:///p/users/dto", "file:///p/usersX")
        assertEquals(listOf("file:///p/usersX"), GroupOrder.remove(saved, "file:///p/users"))
    }
}
