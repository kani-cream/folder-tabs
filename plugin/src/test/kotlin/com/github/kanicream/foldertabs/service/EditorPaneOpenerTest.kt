package com.github.kanicream.foldertabs.service

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Design section 13 / issue #29: a header click opens the file in the header's own split pane.
 * The platform opens into its *current* window, which only follows the keyboard focus, so the
 * opener moves the focus into the pane first and opens once that focus change has been processed.
 */
class EditorPaneOpenerTest : BasePlatformTestCase() {

    private val editors get() = FileEditorManager.getInstance(project)

    private fun file(path: String): VirtualFile = myFixture.addFileToProject(path, "").virtualFile

    private class Harness(focused: Boolean) {
        val focusRequests = mutableListOf<JComponent>()
        val deferred = mutableListOf<Runnable>()
        val opened = mutableListOf<VirtualFile>()
        var focused = focused

        fun opener(project: com.intellij.openapi.project.Project) = EditorPaneOpener(
            project,
            open = { opened += it },
            isFocusInside = { focused },
            requestFocus = { focusRequests += it },
            afterFocusSettles = { deferred += it },
        )

        fun runDeferred() = deferred.toList().also { deferred.clear() }.forEach { it.run() }
    }

    fun testWithoutAPaneTheFileOpensImmediately() {
        val a = file("users/a.go")
        val h = Harness(focused = false)
        h.opener(project).open(a, pane = null)
        assertEquals(listOf(a), h.opened)
        assertTrue(h.focusRequests.isEmpty())
        assertTrue(h.deferred.isEmpty())
    }

    fun testWhenThePaneAlreadyOwnsTheFocusTheFileOpensImmediately() {
        val a = file("users/a.go")
        val h = Harness(focused = true)
        h.opener(project).open(a, pane = JPanel())
        assertEquals(listOf(a), h.opened)
        assertTrue("no focus request needed", h.focusRequests.isEmpty())
        assertTrue(h.deferred.isEmpty())
    }

    fun testWhenAnotherPaneOwnsTheFocusTheOpenWaitsForTheFocusToMoveIntoThisPane() {
        val a = file("users/a.go")
        val pane = JPanel()
        val h = Harness(focused = false)
        h.opener(project).open(a, pane)

        assertEquals("focus goes to the header's pane first", listOf<JComponent>(pane), h.focusRequests)
        assertTrue("open must not run before the focus change is processed", h.opened.isEmpty())

        h.runDeferred()
        assertEquals(listOf(a), h.opened)
    }

    fun testADeferredOpenIsSkippedWhenTheFileBecameInvalidMeanwhile() {
        val a = file("users/a.go")
        val h = Harness(focused = false)
        h.opener(project).open(a, JPanel())
        com.intellij.openapi.application.WriteAction.runAndWait<Exception> { a.delete(this) }
        h.runDeferred()
        assertTrue(h.opened.isEmpty())
    }

    fun testAnInvalidFileIsIgnored() {
        val a = file("users/a.go")
        com.intellij.openapi.application.WriteAction.runAndWait<Exception> { a.delete(this) }
        val h = Harness(focused = false)
        h.opener(project).open(a, JPanel())
        assertTrue(h.opened.isEmpty())
        assertTrue(h.focusRequests.isEmpty())
    }

    fun testTheDefaultOpenerOpensThroughTheEditorManager() {
        val a = file("users/a.go")
        EditorPaneOpener(project).open(a, pane = null)
        assertTrue(editors.isFileOpen(a))
    }
}
