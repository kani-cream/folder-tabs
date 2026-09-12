package com.github.kanicream.foldertabs.grouping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Design section 7 / 24.1: natural, case-insensitive ascending order. */
class NaturalOrderComparatorTest {

    private fun sorted(vararg names: String): List<String> =
        names.toList().sortedWith(NaturalOrderComparator)

    @Test
    fun `alphabetic ascending`() {
        assertEquals(listOf("a.go", "b.go", "c.go"), sorted("c.go", "a.go", "b.go"))
    }

    @Test
    fun `equal numbers with different digit counts fall through to the rest of the name`() {
        assertEquals(listOf("a01a.go", "a1b.go"), sorted("a1b.go", "a01a.go"))
        assertEquals("same number, same rest: equal", 0, NaturalOrderComparator.compare("a01.go", "a1.go"))
    }

    @Test
    fun `numeric suffixes compare as numbers`() {
        assertEquals(
            listOf("file2.go", "file9.go", "file10.go"),
            sorted("file10.go", "file9.go", "file2.go"),
        )
    }

    @Test
    fun `case is ignored`() {
        assertEquals(listOf("alpha", "Beta", "gamma"), sorted("gamma", "Beta", "alpha"))
    }

    @Test
    fun `case only difference is equal`() {
        assertEquals(0, NaturalOrderComparator.compare("Users", "users"))
    }

    @Test
    fun `prefix sorts before longer name`() {
        assertTrue(NaturalOrderComparator.compare("user", "users") < 0)
    }

    @Test
    fun `paths compare segment-wise like names`() {
        assertEquals(
            listOf("admin/users", "api/users", "orders"),
            sorted("orders", "api/users", "admin/users"),
        )
    }
}
