package com.github.kanicream.foldertabs.grouping

import org.junit.Assert.assertEquals
import org.junit.Test

/** Design section 6 / 6.5 / 24.1: label depth as lower bound, unique suffix on collision. */
class MinimalUniquePathResolverTest {

    private fun segs(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }

    private fun outside(vararg paths: String) =
        paths.associateWith { DirectoryLabelSource(segs(it), projectRelative = false) }

    private fun inside(vararg paths: String) =
        paths.associateWith { DirectoryLabelSource(segs(it), projectRelative = true) }

    private fun resolve(sources: Map<String, DirectoryLabelSource>, depth: Int = 1, project: String = "proj") =
        MinimalUniquePathResolver.resolve(sources, GroupLabelPolicy(depth, project))

    // ---- depth 1: original Minimal Unique Path behaviour ---------------------------------

    @Test
    fun `distinct names keep the directory name`() {
        assertEquals(mapOf("users" to "users", "orders" to "orders"), resolve(outside("users", "orders")))
    }

    @Test
    fun `same name gets one parent segment`() {
        assertEquals(
            mapOf("hoge/users" to "hoge/users", "huga/users" to "huga/users", "orders" to "orders"),
            resolve(outside("hoge/users", "huga/users", "orders")),
        )
    }

    @Test
    fun `same parent name requires two parent segments`() {
        assertEquals(
            mapOf("aaa/hoge/users" to "aaa/hoge/users", "bbb/hoge/users" to "bbb/hoge/users"),
            resolve(outside("aaa/hoge/users", "bbb/hoge/users")),
        )
    }

    @Test
    fun `only colliding names are expanded`() {
        val result = resolve(outside("api/users", "admin/users", "api/orders"))
        assertEquals("api/users", result["api/users"])
        assertEquals("admin/users", result["admin/users"])
        assertEquals("orders", result["api/orders"])
    }

    @Test
    fun `three way collision expands each to minimal depth`() {
        val result = resolve(outside("a/x/users", "b/x/users", "c/users"))
        assertEquals("a/x/users", result["a/x/users"])
        assertEquals("b/x/users", result["b/x/users"])
        assertEquals("c/users", result["c/users"])
    }

    @Test
    fun `identical segment lists for different keys do not loop forever`() {
        val result = MinimalUniquePathResolver.resolve(
            mapOf(
                "k1" to DirectoryLabelSource(listOf("a", "users"), false),
                "k2" to DirectoryLabelSource(listOf("a", "users"), false),
            ),
            GroupLabelPolicy(1, "proj"),
        )
        assertEquals("a/users", result["k1"])
        assertEquals("a/users", result["k2"])
    }

    @Test
    fun `empty non-project segment list falls back to Other label`() {
        val result = MinimalUniquePathResolver.resolve(
            mapOf("k" to DirectoryLabelSource(emptyList(), false)),
            GroupLabelPolicy(1, "proj"),
        )
        assertEquals(MinimalUniquePathResolver.FALLBACK_NAME, result["k"])
    }

    // ---- depth >= 2 and project root prefix (design 6.5) --------------------------------

    @Test
    fun `depth 2 shows parent and name`() {
        assertEquals("main/users", resolve(inside("src/main/users"), depth = 2)["src/main/users"])
    }

    @Test
    fun `depth beyond project depth shows project root prefix`() {
        assertEquals(
            "~/proj/src/main/users",
            resolve(inside("src/main/users"), depth = 5)["src/main/users"],
        )
    }

    @Test
    fun `project root depth shows full relative path with prefix`() {
        assertEquals(
            "~/proj/src/main/users",
            resolve(inside("src/main/users"), depth = GroupLabelPolicy.PROJECT_ROOT_DEPTH)["src/main/users"],
        )
    }

    @Test
    fun `depth equal to project depth already reaches the root`() {
        assertEquals("~/proj/docs", resolve(inside("docs"), depth = 1)["docs"])
    }

    @Test
    fun `project root directory itself is labelled with the project name`() {
        val result = MinimalUniquePathResolver.resolve(
            mapOf("root" to DirectoryLabelSource(emptyList(), projectRelative = true)),
            GroupLabelPolicy(2, "proj"),
        )
        assertEquals("~/proj", result["root"])
    }

    @Test
    fun `depth 2 collision still expands to three segments`() {
        val result = resolve(inside("a/x/users", "b/x/users"), depth = 2)
        assertEquals("~/proj/a/x/users", result["a/x/users"])
        assertEquals("~/proj/b/x/users", result["b/x/users"])
    }

    @Test
    fun `depth 2 collision expands without prefix when deeper than project`() {
        val result = resolve(inside("m/a/x/users", "m/b/x/users"), depth = 2)
        assertEquals("a/x/users", result["m/a/x/users"])
        assertEquals("b/x/users", result["m/b/x/users"])
    }

    @Test
    fun `outside project paths never get the prefix`() {
        val result = resolve(outside("/Users/me/.config/foo"), depth = GroupLabelPolicy.PROJECT_ROOT_DEPTH)
        assertEquals("Users/me/.config/foo", result["/Users/me/.config/foo"])
    }

    @Test
    fun `outside path shorter than depth shows what it has`() {
        assertEquals("foo", resolve(outside("foo"), depth = 3)["foo"])
    }

    @Test
    fun `display separator is always slash`() {
        val result = MinimalUniquePathResolver.resolve(
            mapOf(
                "w" to DirectoryLabelSource(listOf("C:", "api", "users"), false),
                "u" to DirectoryLabelSource(listOf("home", "admin", "users"), false),
            ),
            GroupLabelPolicy(1, "proj"),
        )
        assertEquals("api/users", result["w"])
        assertEquals("admin/users", result["u"])
    }
}
