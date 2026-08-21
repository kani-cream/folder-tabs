package com.github.kanicream.foldertabs.grouping

import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Design section 24.1: grouping by immediate parent on a real (temp) VFS. */
class DirectoryGroupBuilderTest : BasePlatformTestCase() {

    private fun file(path: String): VirtualFile = myFixture.addFileToProject(path, "").virtualFile

    private fun builder(depth: Int = 1, basePath: String? = null) =
        DirectoryGroupBuilder(projectBasePath = basePath, labelPolicy = GroupLabelPolicy(depth, "proj"), isModified = { false })

    fun testSameParentFilesShareOneGroup() {
        val model = builder().build(listOf(file("users/a.go"), file("users/b.go")))
        assertEquals(1, model.groups.size)
        assertEquals(listOf("a.go", "b.go"), model.groups.single().files.map { it.displayName })
    }

    fun testDifferentParentsAreDifferentGroups() {
        val model = builder().build(listOf(file("users/a.go"), file("orders/b.go")))
        assertEquals(listOf("orders", "users"), model.groups.map { it.displayName })
    }

    fun testSameNameDirectoriesStaySeparateWithMinimalUniquePath() {
        val model = builder().build(listOf(file("hoge/users/a.go"), file("huga/users/b.go")))
        assertEquals(listOf("hoge/users", "huga/users"), model.groups.map { it.displayName })
    }

    fun testNestedDirectoryIsNotMergedIntoParent() {
        val model = builder().build(listOf(file("users/controller.go"), file("users/dto/user.go")))
        assertEquals(listOf("dto", "users"), model.groups.map { it.displayName })
    }

    fun testFilesSortNaturally() {
        val model = builder().build(listOf(file("x/file10.go"), file("x/file9.go"), file("x/file2.go")))
        assertEquals(listOf("file2.go", "file9.go", "file10.go"), model.groups.single().files.map { it.displayName })
    }

    fun testParentlessFileFallsIntoOtherGroup() {
        val model = builder().build(listOf(LightVirtualFile("scratch.txt")))
        val group = model.groups.single()
        assertNull(group.directory)
        assertEquals(MinimalUniquePathResolver.FALLBACK_NAME, group.displayName)
    }

    fun testOtherGroupFollowsSavedOrderViaItsOrderKey() {
        val users = file("users/a.go")
        val builder = DirectoryGroupBuilder(
            projectBasePath = null,
            labelPolicy = GroupLabelPolicy(1, "proj"),
            savedGroupOrder = listOf(DirectoryGroupModel.OTHER_ORDER_KEY, users.parent.url),
            isModified = { false },
        )
        val model = builder.build(listOf(users, LightVirtualFile("scratch.txt")))
        assertEquals(listOf(MinimalUniquePathResolver.FALLBACK_NAME, "users"), model.groups.map { it.displayName })
        assertEquals(DirectoryGroupModel.OTHER_ORDER_KEY, model.groups.first().orderKey)
        assertEquals(users.parent.url, model.groups.last().orderKey)
    }

    fun testDirectoriesAndDuplicatesAreIgnored() {
        val f = file("users/a.go")
        val model = builder().build(listOf(f, f, f.parent))
        assertEquals(1, model.groups.single().files.size)
    }

    fun testDepthTwoShowsParentSegment() {
        val model = builder(depth = 2).build(listOf(file("src/main/users/a.go")))
        assertEquals("main/users", model.groups.single().displayName)
    }

    fun testProjectRootPrefixWhenLabelReachesBase() {
        val f = file("docs/a.md")
        val base = f.parent.parent.path
        val model = builder(depth = 3, basePath = base).build(listOf(f))
        assertEquals("~/proj/docs", model.groups.single().displayName)
    }

    fun testEmptyInputGivesEmptyModel() {
        assertTrue(builder().build(emptyList()).groups.isEmpty())
    }

    fun testGroupOfFindsContainingGroup() {
        val a = file("users/a.go")
        val model = builder().build(listOf(a, file("orders/b.go")))
        assertEquals("users", model.groupOf(a)?.displayName)
        assertNull(model.groupOf(null))
    }
}
