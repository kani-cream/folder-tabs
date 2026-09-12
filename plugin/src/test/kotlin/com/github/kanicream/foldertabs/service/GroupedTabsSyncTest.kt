package com.github.kanicream.foldertabs.service

import com.github.kanicream.foldertabs.FolderTabsPlatformTestCase
import com.github.kanicream.foldertabs.order.FileOrderState
import com.github.kanicream.foldertabs.order.GroupOrderState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil

/** Design sections 7.1, 10, 19, 24.2: VFS follow-up, group order, modified flag. */
class GroupedTabsSyncTest : FolderTabsPlatformTestCase() {

    /** The modified-flag tests edit the document, so it must not start empty. */
    override val fileContent: String = "x"

    override fun setUp() {
        super.setUp()
        GroupOrderState.getInstance(project).update { emptyList() }
        FileOrderState.getInstance(project).update { emptyMap() }
    }

    override fun tearDown() {
        try {
            GroupOrderState.getInstance(project).update { emptyList() }
            FileOrderState.getInstance(project).update { emptyMap() }
        } finally {
            super.tearDown()
        }
    }

    private fun groupNames() = service.model.groups.map { it.displayName }

    fun testDirectoryRenameUpdatesGroupLabel() {
        val a = open("users/a.go")
        assertEquals(listOf("users"), groupNames())
        WriteAction.runAndWait<Exception> { a.parent.rename(this, "accounts") }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(listOf("accounts"), groupNames())
    }

    fun testFileMoveChangesGroup() {
        val a = open("users/a.go")
        val orders = myFixture.addFileToProject("orders/b.go", "").virtualFile.parent
        WriteAction.runAndWait<Exception> { a.move(this, orders) }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(listOf("orders"), groupNames())
    }

    fun testUnrelatedVfsEventDoesNotRebuild() {
        open("users/a.go")
        val before = service.rebuildCount
        myFixture.addFileToProject("elsewhere/z.go", "") // create event, unrelated
        WriteAction.runAndWait<Exception> {
            myFixture.tempDirFixture.getFile("elsewhere/z.go")!!.rename(this, "y.go") // unrelated rename
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(before, service.rebuildCount)
    }

    fun testReorderPersistsAndNewGroupsFollow() {
        open("a/1.go")
        open("b/2.go")
        val (ga, gb) = service.model.groups
        service.reorderGroups(listOf(gb, ga))
        flush()
        assertEquals(listOf("b", "a"), groupNames())
        assertEquals(listOf(gb.directory!!.url, ga.directory!!.url), GroupOrderState.getInstance(project).savedUrls)
        open("c/3.go")
        assertEquals(listOf("b", "a", "c"), groupNames())
    }

    fun testSavedOrderFollowsDirectoryRename() {
        val a = open("a/1.go")
        open("b/2.go")
        val (ga, gb) = service.model.groups
        service.reorderGroups(listOf(gb, ga))
        flush()
        WriteAction.runAndWait<Exception> { a.parent.rename(this, "zzz") }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(listOf("b", "zzz"), groupNames())
    }

    private fun fileNames(group: Int = 0) = service.model.groups[group].files.map { it.displayName }

    private fun closeAll() {
        editors.openFiles.forEach { editors.closeFile(it) }
        flush()
        assertTrue(editors.openFiles.isEmpty())
    }

    private fun reopen(file: VirtualFile) {
        editors.openFile(file, true)
        flush()
    }

    // Issue #32: saved orders must follow rename / delete even while no editor is open.

    fun testSavedGroupOrderFollowsDirectoryRenameWhileNoFilesAreOpen() {
        val a = open("a/1.go")
        val b = open("b/2.go")
        val (ga, gb) = service.model.groups
        service.reorderGroups(listOf(gb, ga))
        flush()
        closeAll()
        WriteAction.runAndWait<Exception> { b.parent.rename(this, "zzz") }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(listOf(b.parent.url, a.parent.url), GroupOrderState.getInstance(project).savedUrls)
        reopen(a)
        reopen(b)
        assertEquals(listOf("zzz", "a"), groupNames())
    }

    fun testSavedFileOrderFollowsDirectoryRenameWhileNoFilesAreOpen() {
        val a = open("users/a.go")
        val b = open("users/b.go")
        service.reorderFiles(service.model.groups.single(), listOf(b, a))
        flush()
        closeAll()
        WriteAction.runAndWait<Exception> { a.parent.rename(this, "accounts") }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(mapOf(a.parent.url to listOf(b.url, a.url)), FileOrderState.getInstance(project).saved)
        reopen(a)
        reopen(b)
        assertEquals(listOf("accounts"), groupNames())
        assertEquals(listOf("b.go", "a.go"), fileNames())
    }

    fun testSavedFileOrderFollowsFileRenameWhileNoFilesAreOpen() {
        val a = open("users/a.go")
        val b = open("users/b.go")
        service.reorderFiles(service.model.groups.single(), listOf(b, a))
        flush()
        closeAll()
        WriteAction.runAndWait<Exception> { b.rename(this, "z.go") }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        reopen(a)
        reopen(b)
        assertEquals(listOf("z.go", "a.go"), fileNames())
    }

    fun testDeletingDirectoryWhileNoFilesAreOpenDropsItsSavedOrders() {
        val a = open("a/1.go")
        val b = open("b/2.go")
        val (ga, gb) = service.model.groups
        service.reorderGroups(listOf(gb, ga))
        service.reorderFiles(gb, listOf(b))
        flush()
        assertTrue(b.parent.url in GroupOrderState.getInstance(project).savedUrls)
        assertTrue(b.parent.url in FileOrderState.getInstance(project).saved.keys)
        closeAll()
        WriteAction.runAndWait<Exception> { b.parent.delete(this) }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(listOf(a.parent.url), GroupOrderState.getInstance(project).savedUrls)
        assertEquals(emptyMap<String, List<String>>(), FileOrderState.getInstance(project).saved)
    }

    fun testFileReorderPersistsAndSurvivesRefresh() {
        val a = open("users/a.go")
        val b = open("users/b.go")
        assertEquals(listOf("a.go", "b.go"), fileNames())
        service.reorderFiles(service.model.groups.single(), listOf(b, a))
        flush()
        assertEquals(listOf("b.go", "a.go"), fileNames())
        assertEquals(mapOf(a.parent.url to listOf(b.url, a.url)), FileOrderState.getInstance(project).saved)
        open("users/c.go") // new files keep the default order after the dragged ones
        assertEquals(listOf("b.go", "a.go", "c.go"), fileNames())
    }

    fun testFileReorderDoesNotTouchOtherGroups() {
        val a = open("users/a.go")
        val b = open("users/b.go")
        open("orders/o.go")
        val users = service.model.groups.first { it.displayName == "users" }
        service.reorderFiles(users, listOf(b, a))
        flush()
        assertEquals(listOf("o.go"), service.model.groups.first { it.displayName == "orders" }.files.map { it.displayName })
        assertEquals(setOf(a.parent.url), FileOrderState.getInstance(project).saved.keys)
    }

    fun testSavedFileOrderFollowsDirectoryRename() {
        val a = open("users/a.go")
        val b = open("users/b.go")
        service.reorderFiles(service.model.groups.single(), listOf(b, a))
        flush()
        WriteAction.runAndWait<Exception> { a.parent.rename(this, "accounts") }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(listOf("accounts"), groupNames())
        assertEquals(listOf("b.go", "a.go"), fileNames())
        assertEquals(setOf(a.parent.url), FileOrderState.getInstance(project).saved.keys)
    }

    fun testSavedFileOrderFollowsFileRename() {
        val a = open("users/a.go")
        val b = open("users/b.go")
        service.reorderFiles(service.model.groups.single(), listOf(b, a))
        flush()
        WriteAction.runAndWait<Exception> { b.rename(this, "z.go") }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(listOf("z.go", "a.go"), fileNames())
    }

    fun testSavedFileOrderDropsDeletedFiles() {
        val a = open("users/a.go")
        val b = open("users/b.go")
        service.reorderFiles(service.model.groups.single(), listOf(b, a))
        flush()
        WriteAction.runAndWait<Exception> { b.delete(this) }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(mapOf(a.parent.url to listOf(a.url)), FileOrderState.getInstance(project).saved)
    }

    fun testEditAndSaveInTheSameEdtTurnLeavesTheFileUnmodified() {
        val a = open("users/a.go")
        val doc = FileDocumentManager.getInstance().getDocument(a)!!
        // Edit and save before the queued modified-flag update has run (like reformat-on-save):
        // the update queued for the edit must not re-apply the stale "modified" state afterwards.
        WriteCommandAction.runWriteCommandAction(project) {
            doc.insertString(0, "y")
            FileDocumentManager.getInstance().saveDocument(doc)
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertFalse(service.model.groups.single().files.single().modified)
    }

    fun testModifiedFlagFollowsDocumentEdits() {
        val a = open("users/a.go")
        assertFalse(service.model.groups.single().files.single().modified)
        val doc = FileDocumentManager.getInstance().getDocument(a)!!
        WriteCommandAction.runWriteCommandAction(project) { doc.insertString(0, "y") }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertTrue(service.model.groups.single().files.single().modified)
        WriteAction.runAndWait<Exception> { FileDocumentManager.getInstance().saveDocument(doc) }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertFalse(service.model.groups.single().files.single().modified)
    }
}
