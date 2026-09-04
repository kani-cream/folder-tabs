package com.github.kanicream.foldertabs.ui

import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.github.kanicream.foldertabs.model.FileTabModel
import com.github.kanicream.foldertabs.model.GroupedTabsModel
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import javax.swing.JComponent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.KeyboardFocusManager
import javax.swing.JPanel

/** The header paints its selected tabs like the standard editor tabs: "active" only while its editor has focus. */
class GroupedTabsPanelActiveTest : BasePlatformTestCase() {

    private object NoopNavigator : FolderTabsNavigator {
        override fun openFile(file: VirtualFile, pane: JComponent?) = Unit
        override fun openGroup(group: DirectoryGroupModel, pane: JComponent?) = Unit
        override fun closeFile(file: VirtualFile, headerContext: DataContext) = Unit
        override fun closeGroup(group: DirectoryGroupModel, headerContext: DataContext) = Unit
        override fun reorderGroups(groupsInNewOrder: List<DirectoryGroupModel>) = Unit
        override fun reorderFiles(group: DirectoryGroupModel, filesInNewOrder: List<VirtualFile>) = Unit
    }

    private fun isActive(panel: GroupedTabsPanel): Boolean = panel.stripsForTest().all { it.isActiveForTest() }

    fun testStripsFollowTheEditorActiveState() {
        var active = false
        val panel = GroupedTabsPanel(project, LightVirtualFile("a.txt"), NoopNavigator, isEditorActive = { active })
        Disposer.register(testRootDisposable, panel)
        assertFalse(isActive(panel))
        active = true
        assertTrue(isActive(panel))
    }

    fun testFocusListenerIsRemovedOnDispose() {
        val panel = GroupedTabsPanel(project, LightVirtualFile("a.txt"), NoopNavigator, isEditorActive = { true })
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val listenerOf = { focusManager.getPropertyChangeListeners(GroupedTabsPanel.FOCUS_OWNER_PROPERTY).count { it === panel.focusListenerForTest() } }
        assertEquals(1, listenerOf())
        Disposer.dispose(panel)
        assertEquals(0, listenerOf())
    }

    fun testPanelIsActiveByDefault() {
        val panel = GroupedTabsPanel(project, LightVirtualFile("a.txt"), NoopNavigator)
        Disposer.register(testRootDisposable, panel)
        assertTrue(isActive(panel))
    }

    fun testStripsSendTheFocusToTheEditor() {
        val editor = JPanel()
        val own = LightVirtualFile("a.txt")
        val panel = GroupedTabsPanel(project, own, NoopNavigator, editorFocusTarget = { editor })
        Disposer.register(testRootDisposable, panel)
        val files = listOf(own, LightVirtualFile("b.txt")).map { FileTabModel(it, it.name, it.name, modified = false) }
        val group = DirectoryGroupModel(directory = null, displayName = "Other", fullPath = "Other", files = files)
        panel.render(GroupedTabsModel(listOf(group)))
        panel.stripsForTest().flatMap { it.tabInfosForTest() }.also { assertTrue(it.isNotEmpty()) }
            .forEach { assertSame(editor, it.getPreferredFocusableComponent()) }
    }
}
