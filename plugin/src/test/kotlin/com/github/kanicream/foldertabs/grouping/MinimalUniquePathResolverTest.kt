package com.github.kanicream.foldertabs.grouping

import org.junit.Assert.assertEquals
import org.junit.Test

/** Design section 6 / 24.1: shortest distinguishing suffix per directory. */
class MinimalUniquePathResolverTest {

    private fun segs(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }

    private fun resolve(vararg paths: String): Map<String, String> =
        MinimalUniquePathResolver.resolve(paths.associateWith { segs(it) })

    @Test
    fun `distinct names keep the directory name`() {
        assertEquals(
            mapOf("users" to "users", "orders" to "orders"),
            resolve("users", "orders"),
        )
    }

    @Test
    fun `same name gets one parent segment`() {
        assertEquals(
            mapOf("hoge/users" to "hoge/users", "huga/users" to "huga/users", "orders" to "orders"),
            resolve("hoge/users", "huga/users", "orders"),
        )
    }

    @Test
    fun `same parent name requires two parent segments`() {
        assertEquals(
            mapOf("aaa/hoge/users" to "aaa/hoge/users", "bbb/hoge/users" to "bbb/hoge/users"),
            resolve("aaa/hoge/users", "bbb/hoge/users"),
        )
    }

    @Test
    fun `deep common suffix expands until unique`() {
        assertEquals(
            mapOf(
                "moduleA/src/main/users" to "moduleA/src/main/users",
                "moduleB/src/main/users" to "moduleB/src/main/users",
            ),
            resolve("moduleA/src/main/users", "moduleB/src/main/users"),
        )
    }

    @Test
    fun `only colliding names are expanded`() {
        val result = resolve("api/users", "admin/users", "api/orders")
        assertEquals("api/users", result["api/users"])
        assertEquals("admin/users", result["admin/users"])
        assertEquals("orders", result["api/orders"])
    }

    @Test
    fun `three way collision expands each to minimal depth`() {
        val result = resolve("a/x/users", "b/x/users", "c/users")
        assertEquals("a/x/users", result["a/x/users"])
        assertEquals("b/x/users", result["b/x/users"])
        assertEquals("c/users", result["c/users"])
    }

    @Test
    fun `out of project absolute paths are resolved the same way`() {
        val result = resolve("/Users/me/.config/foo", "/project/src/foo")
        assertEquals(".config/foo", result["/Users/me/.config/foo"])
        assertEquals("src/foo", result["/project/src/foo"])
    }

    @Test
    fun `shorter path that runs out of segments falls back to its full path`() {
        // "users" at the root collides with "x/users"; the root one cannot grow.
        val result = resolve("users", "x/users")
        assertEquals("users", result["users"])
        assertEquals("x/users", result["x/users"])
    }

    @Test
    fun `identical segment lists for different keys do not loop forever`() {
        val result = MinimalUniquePathResolver.resolve(
            mapOf("k1" to listOf("a", "users"), "k2" to listOf("a", "users")),
        )
        assertEquals("a/users", result["k1"])
        assertEquals("a/users", result["k2"])
    }

    @Test
    fun `empty segment list falls back to Other label`() {
        val result = MinimalUniquePathResolver.resolve(mapOf("k" to emptyList()))
        assertEquals(MinimalUniquePathResolver.FALLBACK_NAME, result["k"])
    }

    @Test
    fun `display separator is always slash`() {
        val result = MinimalUniquePathResolver.resolve(
            mapOf("w" to listOf("C:", "api", "users"), "u" to listOf("home", "admin", "users")),
        )
        assertEquals("api/users", result["w"])
        assertEquals("admin/users", result["u"])
    }
}
