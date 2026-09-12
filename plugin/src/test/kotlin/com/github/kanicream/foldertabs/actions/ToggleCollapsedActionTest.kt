package com.github.kanicream.foldertabs.actions

import com.github.kanicream.foldertabs.FolderTabsPlatformTestCase
import com.github.kanicream.foldertabs.settings.FolderTabsSettings
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent

/** Issue #26: the Collapse Folder Tabs toggle is registered, assignable, and drives the project's headers. */
class ToggleCollapsedActionTest : FolderTabsPlatformTestCase() {

    private val action: ToggleAction get() = ActionManager.getInstance().getAction(ToggleCollapsedAction.ID) as ToggleAction

    private fun event() = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))

    override fun tearDown() {
        try {
            service.setHeadersCollapsed(false)
        } finally {
            super.tearDown()
        }
    }

    fun testRegisteredInTheWindowEditorTabsMenuWithoutAShortcut() {
        assertTrue(action is ToggleCollapsedAction)
        val group = ActionManager.getInstance().getAction("EditorTabsGroup") as DefaultActionGroup
        assertTrue(ToggleCollapsedAction.ID in group.childActionsOrStubs.map { ActionManager.getInstance().getId(it) })
        assertEmpty(action.shortcutSet.shortcuts.toList())
    }

    fun testToggleReflectsAndDrivesTheProjectState() {
        val a = open("users/a.go")
        assertFalse(action.isSelected(event()))
        action.setSelected(event(), true)
        assertTrue(action.isSelected(event()))
        assertTrue(service.panelForTest(editorOf(a)).isCollapsed)
        action.setSelected(event(), false)
        assertFalse(service.panelForTest(editorOf(a)).isCollapsed)
    }

    fun testEnabledWheneverAProjectIsAtHand() {
        val e = event()
        action.update(e)
        assertTrue(e.presentation.isEnabled)
    }

    fun testDisabledWhileThePluginIsTurnedOff() {
        val settings = FolderTabsSettings.getInstance()
        try {
            settings.enabled = false
            val e = event()
            action.update(e)
            assertFalse(e.presentation.isEnabled)
        } finally {
            settings.enabled = true
        }
    }
}
