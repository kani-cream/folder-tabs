package com.github.kanicream.foldertabs.grouping

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectoryPathSegmentsTest {

    @Test
    fun `inside project is relative to base`() {
        assertEquals(
            listOf("backend", "api", "users"),
            DirectoryPathSegments.of("/project/backend/api/users", "/project"),
        )
    }

    @Test
    fun `project base itself keeps its own name`() {
        assertEquals(listOf("project"), DirectoryPathSegments.of("/work/project", "/work/project"))
    }

    @Test
    fun `outside project uses full path`() {
        assertEquals(
            listOf("Users", "me", ".config", "foo"),
            DirectoryPathSegments.of("/Users/me/.config/foo", "/project"),
        )
    }

    @Test
    fun `sibling with base as prefix is not treated as inside`() {
        assertEquals(
            listOf("work", "project-other", "src"),
            DirectoryPathSegments.of("/work/project-other/src", "/work/project"),
        )
    }

    @Test
    fun `null base uses full path`() {
        assertEquals(listOf("a", "b"), DirectoryPathSegments.of("/a/b", null))
    }

    @Test
    fun `trailing slash on base is tolerated`() {
        assertEquals(listOf("src"), DirectoryPathSegments.of("/p/src", "/p/"))
    }
}
