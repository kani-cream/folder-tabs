package com.github.kanicream.foldertabs.editor

import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.github.kanicream.foldertabs.ui.FolderTabsNavigator
import com.github.kanicream.foldertabs.ui.GroupedTabsPanel
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

/** Design section 11.2: one header per FileEditor instance; keys compare by identity. */
class EditorHeaderRegistryTest : BasePlatformTestCase() {

    /** A FileEditor whose `equals` says every instance is equal — the registry must not care. */
    private class EqualToEveryoneEditor(private val file: VirtualFile?) : UserDataHolderBase(), FileEditor {
        override fun getComponent(): JComponent = JPanel()
        override fun getPreferredFocusedComponent(): JComponent? = null
        override fun getName(): String = "fake"
        override fun setState(state: FileEditorState) = Unit
        override fun isModified(): Boolean = false
        override fun isValid(): Boolean = true
        override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
        override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
        override fun getFile(): VirtualFile? = file
        override fun dispose() = Unit
        override fun equals(other: Any?): Boolean = other is EqualToEveryoneEditor
        override fun hashCode(): Int = 0
    }

    private object NoopNavigator : FolderTabsNavigator {
        override fun openFile(file: VirtualFile) = Unit
        override fun openGroup(group: DirectoryGroupModel) = Unit
        override fun closeFile(file: VirtualFile, headerContext: DataContext) = Unit
        override fun closeGroup(group: DirectoryGroupModel, headerContext: DataContext) = Unit
        override fun reorderGroups(groupsInNewOrder: List<DirectoryGroupModel>) = Unit
        override fun reorderFiles(group: DirectoryGroupModel, filesInNewOrder: List<VirtualFile>) = Unit
    }

    private fun panel(file: VirtualFile): GroupedTabsPanel =
        GroupedTabsPanel(project, file, NoopNavigator).also { Disposer.register(testRootDisposable, it) }

    fun testEqualButDistinctEditorsGetSeparateEntries() {
        val file = LightVirtualFile("a.txt")
        val registry = EditorHeaderRegistry()
        val e1 = EqualToEveryoneEditor(file)
        val e2 = EqualToEveryoneEditor(file)
        val p1 = panel(file)
        val p2 = panel(file)

        registry.register(e1, p1)
        registry.register(e2, p2)

        assertEquals(2, registry.size)
        assertEquals(2, registry.editorsFor(file).size)
        assertSame(p1, registry.unregister(e1))
        assertFalse(registry.contains(e1))
        assertTrue(registry.contains(e2))
        assertSame(p2, registry.unregister(e2))
        assertNull(registry.unregister(e2))
        assertEquals(0, registry.size)
    }
}
