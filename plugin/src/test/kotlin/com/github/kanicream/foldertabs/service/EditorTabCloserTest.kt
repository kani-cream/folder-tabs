package com.github.kanicream.foldertabs.service

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Design section 15: closing goes through the IDE's own Close Editor action (split-aware) when the
 * header's context names an editor window, and through the project-wide close otherwise.
 */
class EditorTabCloserTest : BasePlatformTestCase() {

    private val editors get() = FileEditorManager.getInstance(project)

    private fun open(path: String) = myFixture.addFileToProject(path, "").virtualFile.also { editors.openFile(it, true) }

    fun testWithoutAnEditorWindowInContextTheFileIsClosedProjectWide() {
        val a = open("users/a.go")
        val b = open("users/b.go")
        var performed = 0
        val closer = EditorTabCloser(project, perform = { _, _ -> performed++ })

        closer.close(a, DataContext.EMPTY_CONTEXT)

        assertEquals(0, performed)
        assertFalse(editors.isFileOpen(a))
        assertTrue(editors.isFileOpen(b))
    }

    fun testWithAnEditorWindowInContextTheIdeCloseEditorActionClosesThatFileInThatWindow() {
        val a = open("users/a.go")
        val events = mutableListOf<Pair<AnAction, AnActionEvent>>()
        // A light test cannot create the platform's EditorWindow: stub the presence check instead.
        val closer = EditorTabCloser(project, perform = { action, event -> events += action to event }, hasEditorWindow = { true })
        val other = open("users/other.go")
        val headerContext = CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
            sink[CommonDataKeys.VIRTUAL_FILE] = other // the header's own file must not win
        }

        closer.close(a, headerContext)

        val (action, event) = events.single()
        assertEquals(IdeActions.ACTION_CLOSE_EDITOR, event.actionManager.getId(action))
        assertEquals(a, event.getData(CommonDataKeys.VIRTUAL_FILE))
        assertEquals(ActionPlaces.EDITOR_TAB, event.place)
        assertTrue(editors.isFileOpen(a)) // the fake performer did not close anything; no project-wide fallback ran
    }

    fun testClosingAFileThatIsNotOpenDoesNothing() {
        val a = open("users/a.go")
        editors.closeFile(a)
        var performed = 0
        EditorTabCloser(project, perform = { _, _ -> performed++ }).close(a, DataContext.EMPTY_CONTEXT)
        assertEquals(0, performed)
    }
}
