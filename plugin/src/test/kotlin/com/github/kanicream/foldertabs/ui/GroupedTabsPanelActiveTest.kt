package com.github.kanicream.foldertabs.ui

import com.github.kanicream.foldertabs.NoopFolderTabsNavigator
import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.github.kanicream.foldertabs.model.FileTabModel
import com.github.kanicream.foldertabs.model.GroupedTabsModel
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.KeyboardFocusManager
import javax.swing.JPanel

/** The header paints its selected tabs like the standard editor tabs: "active" only while its editor has focus. */
class GroupedTabsPanelActiveTest : BasePlatformTestCase() {

    private val noopNavigator = NoopFolderTabsNavigator()

    private fun isActive(panel: GroupedTabsPanel): Boolean = panel.stripsForTest().all { it.isActiveForTest() }

    fun testStripsFollowTheEditorActiveState() {
        var active = false
        val panel = GroupedTabsPanel(project, LightVirtualFile("a.txt"), noopNavigator, isEditorActive = { active })
        Disposer.register(testRootDisposable, panel)
        assertFalse(isActive(panel))
        active = true
        assertTrue(isActive(panel))
    }

    fun testFocusListenerIsRemovedOnDispose() {
        val panel = GroupedTabsPanel(project, LightVirtualFile("a.txt"), noopNavigator, isEditorActive = { true })
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val listenerOf = { focusManager.getPropertyChangeListeners(GroupedTabsPanel.FOCUS_OWNER_PROPERTY).count { it === panel.focusListenerForTest() } }
        assertEquals(1, listenerOf())
        Disposer.dispose(panel)
        assertEquals(0, listenerOf())
    }

    fun testPanelIsActiveByDefault() {
        val panel = GroupedTabsPanel(project, LightVirtualFile("a.txt"), noopNavigator)
        Disposer.register(testRootDisposable, panel)
        assertTrue(isActive(panel))
    }

    fun testStripsSendTheFocusToTheEditor() {
        val editor = JPanel()
        val own = LightVirtualFile("a.txt")
        val panel = GroupedTabsPanel(project, own, noopNavigator, editorFocusTarget = { editor })
        Disposer.register(testRootDisposable, panel)
        val files = listOf(own, LightVirtualFile("b.txt")).map { FileTabModel(it, it.name, it.name, modified = false) }
        val group = DirectoryGroupModel(directory = null, displayName = "Other", fullPath = "Other", files = files)
        panel.render(GroupedTabsModel(listOf(group)))
        panel.stripsForTest().flatMap { it.tabInfosForTest() }.also { assertTrue(it.isNotEmpty()) }
            .forEach { assertSame(editor, it.getPreferredFocusableComponent()) }
    }

    fun testShownCallbackFiresWhenTheHeaderJoinsAWindow() {
        var shown = 0
        val panel = GroupedTabsPanel(project, LightVirtualFile("a.txt"), noopNavigator, onShown = { shown++ })
        Disposer.register(testRootDisposable, panel)
        panel.shownForTest()
        assertEquals(1, shown)
    }
}
