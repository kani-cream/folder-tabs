package com.github.kanicream.foldertabs.order

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Design section 7.2: the user-defined file order inside one directory group. The rules are
 * those of [GroupOrder], applied to the list stored under the group's order key.
 */
class FileOrderTest {

    private val users = "file:///p/users"
    private val orders = "file:///p/orders"

    @Test
    fun `reorder creates the directory entry from the dragged order`() {
        val result = FileOrder.applyReorder(emptyMap(), users, listOf("$users/b.go", "$users/a.go"))
        assertEquals(mapOf(users to listOf("$users/b.go", "$users/a.go")), result)
    }

    @Test
    fun `reorder merges into the directory entry like a group reorder`() {
        // saved: x, a, y, b (x and y are closed); user drags b before a
        val saved = mapOf(users to listOf("x", "a", "y", "b"))
        val result = FileOrder.applyReorder(saved, users, listOf("b", "a"))
        assertEquals(listOf("x", "b", "y", "a"), result.getValue(users))
    }

    @Test
    fun `reorder leaves other directories untouched`() {
        val saved = mapOf(orders to listOf("o1", "o2"))
        val result = FileOrder.applyReorder(saved, users, listOf("b", "a"))
        assertEquals(listOf("o1", "o2"), result.getValue(orders))
        assertEquals(listOf("b", "a"), result.getValue(users))
    }

    @Test
    fun `reorder with an empty dragged order changes nothing`() {
        val saved = mapOf(users to listOf("a"))
        assertEquals(saved, FileOrder.applyReorder(saved, users, emptyList()))
    }

    @Test
    fun `reorder moves the touched directory to the newest position`() {
        val saved = linkedMapOf(users to listOf("a"), orders to listOf("o"))
        val result = FileOrder.applyReorder(saved, users, listOf("a", "b"))
        assertEquals(listOf(orders, users), result.keys.toList())
    }

    @Test
    fun `reorder drops the oldest directories beyond the directory cap`() {
        val saved = linkedMapOf("d1" to listOf("a"), "d2" to listOf("b"), "d3" to listOf("c"))
        val result = FileOrder.applyReorder(saved, "d4", listOf("x"), directoryCap = 3)
        assertEquals(listOf("d2", "d3", "d4"), result.keys.toList())
    }

    @Test
    fun `reorder drops the oldest hidden files beyond the file cap`() {
        val saved = mapOf(users to (1..5).map { "h$it" } + listOf("a"))
        val result = FileOrder.applyReorder(saved, users, listOf("a", "b"), fileCap = 4)
        assertEquals(listOf("h4", "h5", "a", "b"), result.getValue(users))
    }

    @Test
    fun `saved order of an unknown directory is null`() {
        assertNull(FileOrder.savedFor(emptyMap(), users))
    }

    @Test
    fun `rename rewrites the directory key and the file urls under it`() {
        val saved = mapOf(
            users to listOf("$users/a.go", "$users/b.go"),
            "$users/dto" to listOf("$users/dto/u.go"),
            orders to listOf("$orders/o.go"),
        )
        val result = FileOrder.rename(saved, oldUrl = users, newUrl = "file:///p/accounts")
        assertEquals(
            mapOf(
                "file:///p/accounts" to listOf("file:///p/accounts/a.go", "file:///p/accounts/b.go"),
                "file:///p/accounts/dto" to listOf("file:///p/accounts/dto/u.go"),
                orders to listOf("$orders/o.go"),
            ),
            result,
        )
    }

    @Test
    fun `rename of a single file keeps its slot in its directory`() {
        val saved = mapOf(users to listOf("$users/a.go", "$users/b.go"))
        val result = FileOrder.rename(saved, oldUrl = "$users/a.go", newUrl = "$users/z.go")
        assertEquals(listOf("$users/z.go", "$users/b.go"), result.getValue(users))
    }

    @Test
    fun `rename that merges two directory entries keeps the target entry`() {
        val saved = mapOf(users to listOf("$users/a.go"), orders to listOf("$orders/o.go"))
        val result = FileOrder.rename(saved, oldUrl = users, newUrl = orders)
        assertEquals(mapOf(orders to listOf("$orders/o.go")), result)
    }

    @Test
    fun `remove drops the directory entry and everything under it`() {
        val saved = mapOf(users to listOf("$users/a.go"), "$users/dto" to listOf("$users/dto/u.go"), orders to listOf("$orders/o.go"))
        assertEquals(mapOf(orders to listOf("$orders/o.go")), FileOrder.remove(saved, users))
    }

    @Test
    fun `remove of a single file drops only that entry`() {
        val saved = mapOf(users to listOf("$users/a.go", "$users/b.go"))
        assertEquals(mapOf(users to listOf("$users/b.go")), FileOrder.remove(saved, "$users/a.go"))
    }

    @Test
    fun `remove that empties a directory drops the directory entry`() {
        val saved = mapOf(users to listOf("$users/a.go"))
        assertEquals(emptyMap<String, List<String>>(), FileOrder.remove(saved, "$users/a.go"))
    }

    @Test
    fun `sanitize drops blank keys, blank and duplicate urls, empty entries and caps`() {
        val raw = linkedMapOf(
            " " to listOf("a"),
            "d1" to listOf("a", "", "a", "b"),
            "d2" to listOf(" "),
            "d3" to listOf("c"),
        )
        val result = FileOrder.sanitize(raw, directoryCap = 1, fileCap = 1)
        assertEquals(mapOf("d3" to listOf("c")), result)
    }
}
