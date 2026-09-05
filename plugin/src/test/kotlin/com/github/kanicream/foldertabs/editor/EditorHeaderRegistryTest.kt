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
        private val component = JPanel()
        override fun getComponent(): JComponent = component
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
        override fun openFile(file: VirtualFile, pane: JComponent?) = Unit
        override fun openGroup(group: DirectoryGroupModel, pane: JComponent?) = Unit
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

    // ---- design section 13 (v1.3): pane attribution ----

    fun testAttributionIsPerEditorAndGroupsFilesByPane() {
        val a = LightVirtualFile("a.txt")
        val b = LightVirtualFile("b.txt")
        val registry = EditorHeaderRegistry()
        val ea = EqualToEveryoneEditor(a)
        val eb = EqualToEveryoneEditor(b)
        val ea2 = EqualToEveryoneEditor(a) // a is open in both panes
        listOf(ea, eb, ea2).forEach { registry.register(it, panel(it.file!!)) }

        assertNull(registry.paneOf(ea))
        registry.attribute(ea, "L")
        registry.attribute(eb, "R")
        registry.attribute(ea2, "R")

        assertEquals("L", registry.paneOf(ea))
        assertEquals(mapOf<Any, Set<VirtualFile>>("L" to setOf(a), "R" to setOf(a, b)), registry.filesByPane())
    }

    fun testUnregisterDropsTheAttribution() {
        val a = LightVirtualFile("a.txt")
        val registry = EditorHeaderRegistry()
        val ea = EqualToEveryoneEditor(a)
        registry.register(ea, panel(a))
        registry.attribute(ea, "L")
        registry.unregister(ea)
        assertNull(registry.paneOf(ea))
        assertTrue(registry.filesByPane().isEmpty())
    }

    fun testEditorOwningFindsTheEditorBehindItsComponent() {
        val a = LightVirtualFile("a.txt")
        val registry = EditorHeaderRegistry()
        val ea = EqualToEveryoneEditor(a)
        val eb = EqualToEveryoneEditor(LightVirtualFile("b.txt"))
        registry.register(ea, panel(a))
        registry.register(eb, panel(eb.file!!))
        assertSame(ea, registry.editorOwning(ea.component))
        assertNull(registry.editorOwning(JPanel()))
    }
}
