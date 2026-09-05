package com.github.kanicream.foldertabs.service

import com.github.kanicream.foldertabs.settings.FolderTabsSettings
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
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
        service.openGroup(users, pane = null)
        flush()
        assertEquals(c, editors.selectedFiles.first())
        assertNotSame(a, editors.selectedFiles.first())
    }

    // ---- design section 13.0 (v1.3): one model per split pane ----

    private fun editorOf(file: VirtualFile): FileEditor = editors.getAllEditors(file).single()

    private fun groupsShownFor(file: VirtualFile) =
        service.panelForTest(editorOf(file)).renderedModelForTest().groups.map { it.displayName }

    /**
     * Fakes the two things a light test cannot get from the platform: the pane a header sits in
     * (`l_*` → "L", `r_*` → "R") and the IDE's per-pane file list (every open file with that prefix).
     */
    private fun usePrefixPanes() {
        fun paneOfName(name: String?) = when (name?.substringBefore('_')) { "l" -> "L"; "r" -> "R"; else -> null }
        service.paneResolverForTest = { editor, _ -> paneOfName(editor.file?.name) }
        service.paneFilesForTest = { pane -> editors.openFiles.filter { paneOfName(it.name) == pane } }
    }

    fun testEachHeaderShowsOnlyTheFilesOfItsPane() {
        usePrefixPanes()
        val l = open("users/l_a.go")
        val r = open("orders/r_b.go")
        listOf(l, r).forEach { service.headerShownForTest(editorOf(it)) }
        flush()
        assertEquals(listOf("users"), groupsShownFor(l))
        assertEquals(listOf("orders"), groupsShownFor(r))
        assertEquals("project-wide model is unchanged", listOf("orders", "users"), service.model.groups.map { it.displayName })
    }

    fun testAHeaderWhosePaneIsUnknownShowsEverything() {
        usePrefixPanes()
        val l = open("users/l_a.go")
        val u = open("misc/u_c.go") // no pane for this header
        listOf(l, u).forEach { service.headerShownForTest(editorOf(it)) }
        flush()
        assertEquals(listOf("misc", "users"), groupsShownFor(u))
    }

    fun testAPaneTheIdeNoLongerListsFallsBackToEverything() {
        usePrefixPanes()
        val l = open("users/l_a.go")
        open("orders/r_b.go")
        service.headerShownForTest(editorOf(l))
        service.paneFilesForTest = { null } // e.g. the window was just disposed
        flush()
        assertEquals(listOf("orders", "users"), groupsShownFor(l))
    }

    fun testAHeaderShownAgainInAnotherPaneFollowsIt() {
        usePrefixPanes()
        val l = open("users/l_a.go")
        val r = open("orders/r_b.go")
        listOf(l, r).forEach { service.headerShownForTest(editorOf(it)) }
        flush()
        assertEquals(listOf("users"), groupsShownFor(l))
        // The tab was dragged into the other split: its header now resolves to R and the IDE lists it there.
        service.paneResolverForTest = { _, _ -> "R" }
        service.paneFilesForTest = { pane -> if (pane == "R") editors.openFiles.toList() else emptyList() }
        service.headerShownForTest(editorOf(l))
        flush()
        assertEquals(listOf("orders", "users"), groupsShownFor(l))
    }

    fun testLastActiveFileIsRememberedPerPane() {
        usePrefixPanes()
        val a = open("users/l_a.go")
        val c = open("users/l_c.go")
        val r = open("users/r_x.go")
        listOf(a, c, r).forEach { service.headerShownForTest(editorOf(it)) }
        flush()
        service.onSelectionChanged(c, editorOf(c)) // pane L last saw c
        service.onSelectionChanged(r, editorOf(r)) // pane R last saw r
        val usersInL = service.panelForTest(editorOf(a)).renderedModelForTest().groups.single()
        service.openGroup(usersInL, pane = editorOf(a).component)
        flush()
        assertEquals(c, editors.selectedFiles.first())
    }
}
