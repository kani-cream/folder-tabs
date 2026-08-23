package com.github.kanicream.foldertabs.service

import com.github.kanicream.foldertabs.settings.FolderTabsSettings
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Design section 24.2: model follows open / close / selection through the real editor manager. */
class GroupedTabsProjectServiceTest : BasePlatformTestCase() {

    private val service get() = GroupedTabsProjectService.getInstance(project)
    private var savedDepth = 0

    override fun setUp() {
        super.setUp()
        savedDepth = FolderTabsSettings.getInstance().groupLabelDepth
        FolderTabsSettings.getInstance().groupLabelDepth = 1 // labels are plain directory names below
    }

    override fun tearDown() {
        try {
            FolderTabsSettings.getInstance().groupLabelDepth = savedDepth
        } finally {
            super.tearDown()
        }
    }
    private val editors get() = FileEditorManager.getInstance(project)

    private fun open(path: String) = myFixture.addFileToProject(path, "").virtualFile.also {
        editors.openFile(it, true)
        flush()
    }

    private fun flush() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        service.refreshNow()
    }

    fun testOpeningFilesAddsGroups() {
        open("users/a.go")
        open("orders/b.go")
        assertEquals(listOf("orders", "users"), service.model.groups.map { it.displayName })
    }

    fun testClosingLastFileRemovesGroup() {
        val a = open("users/a.go")
        open("orders/b.go")
        editors.closeFile(a)
        flush()
        assertEquals(listOf("orders"), service.model.groups.map { it.displayName })
    }

    fun testHeaderIsAttachedOncePerEditorAndReleasedOnClose() {
        val a = open("users/a.go")
        val before = service.headerCount
        assertTrue("expected at least one header", before >= 1)
        service.onFileOpened(a) // duplicate event must not add a second header
        assertEquals(before, service.headerCount)
        editors.closeFile(a)
        flush()
        assertEquals(0, service.headerCount)
    }

    fun testDisablingRemovesHeadersAndEnablingRestoresThem() {
        val settings = FolderTabsSettings.getInstance()
        try {
            open("users/a.go")
            assertTrue(service.headerCount >= 1)
            settings.enabled = false
            service.applySettings()
            assertEquals(0, service.headerCount)
            open("orders/b.go") // no headers while disabled
            assertEquals(0, service.headerCount)
            settings.enabled = true
            service.applySettings()
            flush()
            assertTrue(service.headerCount >= 2)
        } finally {
            settings.enabled = true
        }
    }

    fun testCloseGroupClosesAllFilesOfThatGroupOnly() {
        open("users/a.go")
        open("users/c.go")
        open("orders/b.go")
        val users = service.model.groups.first { it.displayName == "users" }
        service.closeGroup(users, DataContext.EMPTY_CONTEXT)
        flush()
        assertEquals(listOf("orders"), service.model.groups.map { it.displayName })
    }

    fun testOpenGroupRestoresLastActiveFile() {
        val a = open("users/a.go")
        val c = open("users/c.go")
        open("orders/b.go")
        service.onSelectionChanged(c)
        val users = service.model.groups.first { it.displayName == "users" }
        service.openGroup(users)
        flush()
        assertEquals(c, editors.selectedFiles.first())
        assertNotSame(a, editors.selectedFiles.first())
    }
}
