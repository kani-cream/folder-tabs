package com.github.kanicream.foldertabs.grouping

import org.junit.Assert.assertEquals
import org.junit.Test

class DirectoryPathSegmentsTest {

    @Test
    fun `inside project is relative to base`() {
        assertEquals(
            DirectoryLabelSource(listOf("backend", "api", "users"), projectRelative = true),
            DirectoryPathSegments.of("/project/backend/api/users", "/project"),
        )
    }

    @Test
    fun `project base itself is empty relative`() {
        assertEquals(
            DirectoryLabelSource(emptyList(), projectRelative = true),
            DirectoryPathSegments.of("/work/project", "/work/project"),
        )
    }

    @Test
    fun `outside project uses full path`() {
        assertEquals(
            DirectoryLabelSource(listOf("Users", "me", ".config", "foo"), projectRelative = false),
            DirectoryPathSegments.of("/Users/me/.config/foo", "/project"),
        )
    }

    @Test
    fun `sibling with base as prefix is not treated as inside`() {
        assertEquals(
            DirectoryLabelSource(listOf("work", "project-other", "src"), projectRelative = false),
            DirectoryPathSegments.of("/work/project-other/src", "/work/project"),
        )
    }

    @Test
    fun `null base uses full path`() {
        assertEquals(
            DirectoryLabelSource(listOf("a", "b"), projectRelative = false),
            DirectoryPathSegments.of("/a/b", null),
        )
    }

    @Test
    fun `trailing slash on base is tolerated`() {
        assertEquals(
            DirectoryLabelSource(listOf("src"), projectRelative = true),
            DirectoryPathSegments.of("/p/src", "/p/"),
        )
    }
}
