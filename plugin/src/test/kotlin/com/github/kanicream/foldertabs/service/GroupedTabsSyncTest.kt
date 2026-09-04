package com.github.kanicream.foldertabs.service

import com.github.kanicream.foldertabs.order.FileOrderState
import com.github.kanicream.foldertabs.order.GroupOrderState
import com.github.kanicream.foldertabs.settings.FolderTabsSettings
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Design sections 7.1, 10, 19, 24.2: VFS follow-up, group order, modified flag. */
class GroupedTabsSyncTest : BasePlatformTestCase() {

    private val service get() = GroupedTabsProjectService.getInstance(project)
    private val editors get() = FileEditorManager.getInstance(project)
    private var savedDepth = 0

    override fun setUp() {
        super.setUp()
        savedDepth = FolderTabsSettings.getInstance().groupLabelDepth
        FolderTabsSettings.getInstance().groupLabelDepth = 1
        GroupOrderState.getInstance(project).update { emptyList() }
        FileOrderState.getInstance(project).update { emptyMap() }
    }

    override fun tearDown() {
        try {
            FolderTabsSettings.getInstance().groupLabelDepth = savedDepth
            GroupOrderState.getInstance(project).update { emptyList() }
            FileOrderState.getInstance(project).update { emptyMap() }
        } finally {
            super.tearDown()
        }
    }

    private fun open(path: String): VirtualFile = myFixture.addFileToProject(path, "x").virtualFile.also {
        editors.openFile(it, true)
        flush()
    }

    private fun flush() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        service.refreshNow()
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
