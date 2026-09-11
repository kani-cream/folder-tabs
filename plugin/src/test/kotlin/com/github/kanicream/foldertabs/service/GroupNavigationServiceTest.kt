package com.github.kanicream.foldertabs.service

import com.github.kanicream.foldertabs.model.GroupDirection
import com.github.kanicream.foldertabs.settings.FolderTabsSettings
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Design section 8.4 / issue #24: keyboard navigation between the groups a header shows. */
class GroupNavigationServiceTest : BasePlatformTestCase() {

    private val service get() = GroupedTabsProjectService.getInstance(project)
    private val editors get() = FileEditorManager.getInstance(project)
    private var savedDepth = 0

    override fun setUp() {
        super.setUp()
        savedDepth = FolderTabsSettings.getInstance().groupLabelDepth
        FolderTabsSettings.getInstance().groupLabelDepth = 1
    }

    override fun tearDown() {
        try {
            FolderTabsSettings.getInstance().groupLabelDepth = savedDepth
        } finally {
            super.tearDown()
        }
    }

    private fun open(path: String): VirtualFile = myFixture.addFileToProject(path, "").virtualFile.also {
        editors.openFile(it, true)
        flush()
    }

    private fun flush() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        service.refreshNow()
    }

    private fun editorOf(file: VirtualFile): FileEditor = editors.getAllEditors(file).single()

    private fun selected(): VirtualFile = editors.selectedFiles.first()

    private fun navigate(from: VirtualFile, direction: GroupDirection): Boolean {
        val moved = service.navigateGroup(editorOf(from), direction)
        flush()
        return moved
    }

    // Groups sort as: api, orders, users.

    fun testNextGroupOpensTheLastActiveFileOfTheFollowingGroup() {
        open("api/a.go")
        open("orders/b.go")
        val c = open("users/c.go")
        val d = open("users/d.go")
        service.onSelectionChanged(c, editorOf(c))
        assertTrue(navigate(from = c, direction = GroupDirection.NEXT)) // users -> wraps to api
        assertEquals("a.go", selected().name)
        assertTrue(navigate(from = selected(), direction = GroupDirection.NEXT)) // api -> orders
        assertEquals("b.go", selected().name)
        assertTrue(navigate(from = selected(), direction = GroupDirection.NEXT)) // orders -> users, last active c
        assertEquals(c, selected())
        assertNotSame(d, selected())
    }

    fun testPreviousGroupWrapsFromTheFirstToTheLast() {
        val a = open("api/a.go")
        open("orders/b.go")
        open("users/c.go")
        service.onSelectionChanged(a, editorOf(a))
        assertTrue(navigate(from = a, direction = GroupDirection.PREVIOUS))
        assertEquals("c.go", selected().name)
    }

    fun testASingleGroupCannotBeNavigated() {
        val a = open("users/a.go")
        val b = open("users/b.go") // selected
        assertFalse(service.canNavigateGroups(editorOf(a)))
        assertFalse(navigate(from = a, direction = GroupDirection.NEXT))
        assertEquals("nothing moved", b, selected())
    }

    /** A keystroke can arrive before the coalesced refresh: the closed file must not be reopened. */
    fun testAGroupWhoseLastFileWasJustClosedIsNotNavigatedTo() {
        val a = open("api/a.go")
        val c = open("users/c.go")
        editors.closeFile(a) // the refresh is queued, the header still shows api
        assertFalse(service.navigateGroup(editorOf(c), GroupDirection.NEXT))
        flush()
        assertEquals(listOf(c), editors.openFiles.toList())
    }

    fun testAnEditorWithoutAHeaderCannotBeNavigated() {
        open("api/a.go")
        val c = open("users/c.go")
        val stranger = FakeFileEditor(c)
        assertFalse(service.hasHeader(stranger))
        assertFalse(service.canNavigateGroups(stranger))
        assertFalse(service.navigateGroup(stranger, GroupDirection.NEXT))
    }

    fun testTwoGroupsCanBeNavigated() {
        val a = open("users/a.go")
        open("orders/b.go")
        assertTrue(service.canNavigateGroups(editorOf(a)))
    }

    /** Split panes (design section 13): the neighbour is taken from the groups the editor's own pane shows. */
    fun testNavigationStaysWithinTheGroupsOfTheEditorsPane() {
        fun paneOfName(name: String?) = when (name?.substringBefore('_')) { "l" -> "L"; "r" -> "R"; else -> null }
        service.paneResolverForTest = { editor, _ -> paneOfName(editor.file?.name) }
        service.paneFilesForTest = { pane -> editors.openFiles.filter { paneOfName(it.name) == pane } }
        val la = open("users/l_a.go")
        val lb = open("orders/l_b.go")
        val rx = open("api/r_x.go") // only in pane R: never a neighbour for pane L
        listOf(la, lb, rx).forEach { service.headerShownForTest(editorOf(it)) }
        flush()
        service.onSelectionChanged(lb, editorOf(lb))
        assertTrue(navigate(from = lb, direction = GroupDirection.NEXT)) // L shows orders, users -> users
        assertEquals(la, selected())
        assertTrue(navigate(from = la, direction = GroupDirection.NEXT)) // wraps to orders, not api
        assertEquals(lb, selected())
    }
}
