package com.github.kanicream.foldertabs.actions

import com.github.kanicream.foldertabs.FolderTabsPlatformTestCase
import com.github.kanicream.foldertabs.service.FakeFileEditor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent

/** Issue #24: the Next / Previous Folder Group actions are registered, enabled correctly and move the selection. */
class NavigateGroupActionTest : FolderTabsPlatformTestCase() {

    private fun action(id: String): AnAction = checkNotNull(ActionManager.getInstance().getAction(id)) { "$id is not registered" }

    private fun projectContext(): DataContext = SimpleDataContext.getProjectContext(project)

    private fun update(action: AnAction, context: DataContext): AnActionEvent =
        TestActionEvent.createTestEvent(action, context).also { action.update(it) }

    private fun perform(action: AnAction, context: DataContext) {
        val event = update(action, context)
        assertTrue("${event.presentation.text} should be enabled", event.presentation.isEnabled)
        action.actionPerformed(event)
        flush()
    }

    fun testActionsAreRegisteredWithTheirIds() {
        assertTrue(action(NextGroupAction.ID) is NextGroupAction)
        assertTrue(action(PreviousGroupAction.ID) is PreviousGroupAction)
    }

    fun testActionsAppearInTheWindowEditorTabsMenu() {
        val group = ActionManager.getInstance().getAction("EditorTabsGroup") as DefaultActionGroup
        val ids = group.childActionsOrStubs.map { ActionManager.getInstance().getId(it) }
        assertTrue("$ids", NextGroupAction.ID in ids && PreviousGroupAction.ID in ids)
    }

    fun testActionsHaveNoDefaultShortcut() {
        for (id in listOf(NextGroupAction.ID, PreviousGroupAction.ID)) {
            assertEmpty("$id must be assigned by the user", action(id).shortcutSet.shortcuts.toList())
        }
    }

    /** The header registry is EDT-only state, so the actions must update on the EDT. */
    fun testActionsUpdateOnTheEdt() {
        for (id in listOf(NextGroupAction.ID, PreviousGroupAction.ID)) {
            assertEquals(id, ActionUpdateThread.EDT, action(id).actionUpdateThread)
        }
    }

    fun testDisabledWithoutAProject() {
        val event = update(action(NextGroupAction.ID), DataContext.EMPTY_CONTEXT)
        assertFalse(event.presentation.isEnabled)
    }

    fun testDisabledWhileNoEditorIsOpen() {
        val event = update(action(NextGroupAction.ID), projectContext())
        assertFalse(event.presentation.isEnabled)
    }

    fun testDisabledWithASingleGroup() {
        open("users/a.go")
        val event = update(action(NextGroupAction.ID), projectContext())
        assertFalse(event.presentation.isEnabled)
    }

    fun testNextGroupUsesTheFileEditorFromTheDataContext() {
        val a = open("orders/a.go")
        open("users/b.go")
        val editor = editorOf(a)
        val context = SimpleDataContext.getSimpleContext(PlatformDataKeys.FILE_EDITOR, editor, projectContext())
        perform(action(NextGroupAction.ID), context)
        assertEquals("b.go", editors.selectedFiles.first().name)
    }

    /** A context editor that carries no header (e.g. hosted in a tool window) must not block the shortcut. */
    fun testAContextEditorWithoutAHeaderFallsBackToTheSelectedEditor() {
        open("orders/a.go")
        val b = open("users/b.go") // selected
        val context = SimpleDataContext.getSimpleContext(PlatformDataKeys.FILE_EDITOR, FakeFileEditor(b), projectContext())
        perform(action(NextGroupAction.ID), context)
        assertEquals("a.go", editors.selectedFiles.first().name)
    }

    fun testWithoutAFileEditorTheSelectedEditorIsUsed() {
        open("orders/a.go")
        val b = open("users/b.go") // selected
        perform(action(PreviousGroupAction.ID), projectContext())
        assertEquals("a.go", editors.selectedFiles.first().name)
        assertNotSame(b, editors.selectedFiles.first())
    }
}
