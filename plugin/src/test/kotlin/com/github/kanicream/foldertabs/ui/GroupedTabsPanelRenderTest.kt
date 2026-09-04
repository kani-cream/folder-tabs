package com.github.kanicream.foldertabs.ui

import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.github.kanicream.foldertabs.model.FileTabModel
import com.github.kanicream.foldertabs.model.GroupedTabsModel
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent

/** Issue #16: group tabs must be keyed by a stable identity so a modified-flag flip does not rebuild the strip. */
class GroupedTabsPanelRenderTest : BasePlatformTestCase() {

    private class RecordingNavigator : FolderTabsNavigator {
        val openedGroups = mutableListOf<DirectoryGroupModel>()
        val reordered = mutableListOf<List<DirectoryGroupModel>>()
        val reorderedFiles = mutableListOf<Pair<DirectoryGroupModel, List<VirtualFile>>>()
        val closedGroups = mutableListOf<DirectoryGroupModel>()
        override fun openFile(file: VirtualFile) = Unit
        override fun openGroup(group: DirectoryGroupModel) { openedGroups += group }
        override fun closeFile(file: VirtualFile, headerContext: DataContext) = Unit
        override fun closeGroup(group: DirectoryGroupModel, headerContext: DataContext) { closedGroups += group }
        override fun reorderGroups(groupsInNewOrder: List<DirectoryGroupModel>) { reordered += groupsInNewOrder }
        override fun reorderFiles(group: DirectoryGroupModel, filesInNewOrder: List<VirtualFile>) { reorderedFiles += group to filesInNewOrder }
    }

    private val a = LightVirtualFile("a.txt")
    private val b = LightVirtualFile("b.txt")
    private val c = LightVirtualFile("c.txt")

    private val dirOne = LightVirtualFile("one")
    private val dirTwo = LightVirtualFile("two")

    /** The directory's identity (its URL) is the group's stable key; [modified] flips one file's flag. */
    private fun group(dir: VirtualFile, vararg files: VirtualFile, modified: VirtualFile? = null) = DirectoryGroupModel(
        directory = dir,
        displayName = dir.name,
        fullPath = "/${dir.name}",
        files = files.map { FileTabModel(it, it.name, "/${dir.name}/${it.name}", modified = it == modified) },
    )

    private fun model(modified: VirtualFile? = null): GroupedTabsModel =
        GroupedTabsModel(listOf(group(dirOne, a, b, modified = modified), group(dirTwo, c, modified = modified)))

    private fun panel(navigator: FolderTabsNavigator = RecordingNavigator()): GroupedTabsPanel =
        GroupedTabsPanel(project, a, navigator).also { Disposer.register(testRootDisposable, it) }

    private fun groupInfos(panel: GroupedTabsPanel) = panel.stripsForTest().first().tabInfosForTest()
    private fun fileInfos(panel: GroupedTabsPanel) = panel.stripsForTest().last().tabInfosForTest()

    fun testModifiedFlipKeepsTheGroupTabInfosInPlace() {
        val panel = panel()
        panel.render(model())
        val before = groupInfos(panel)
        assertEquals(2, before.size)

        panel.render(model(modified = a)) // only a file's modified flag changed; same groups, same order
        val after = groupInfos(panel)

        assertEquals("same TabInfo instances expected (in-place update)", before, after)
    }

    fun testGroupOrderChangeStillRebuilds() {
        val panel = panel()
        panel.render(model())
        val before = groupInfos(panel)
        val m = model()
        panel.render(GroupedTabsModel(m.groups.reversed()))
        assertEquals(listOf("two", "one"), groupInfos(panel).map { it.text })
        assertFalse(before == groupInfos(panel))
    }

    // ---- the callbacks must still resolve keys to the *current* model's groups ----

    private fun descendants(x: Component): Sequence<Component> = sequence {
        yield(x)
        if (x is Container) x.components.forEach { yieldAll(descendants(it)) }
    }

    private fun groupLabels(panel: GroupedTabsPanel) =
        descendants(panel.stripsForTest().first().component).filter { it.javaClass.simpleName == "TabLabel" }.toList()

    private fun click(target: Component) {
        val press = MouseEvent(target, MouseEvent.MOUSE_PRESSED, 0L, MouseEvent.BUTTON1_DOWN_MASK, 1, 1, 1, false, MouseEvent.BUTTON1)
        val release = MouseEvent(target, MouseEvent.MOUSE_RELEASED, 0L, MouseEvent.BUTTON1_DOWN_MASK, 1, 1, 1, false, MouseEvent.BUTTON1)
        target.dispatchEvent(press)
        target.dispatchEvent(release)
    }

    fun testClickingAGroupOpensTheCurrentModelsGroup() {
        val navigator = RecordingNavigator()
        val panel = panel(navigator)
        panel.render(model())
        panel.render(model(modified = c)) // the "two" group value changed since the tabs were created
        click(groupLabels(panel)[1])
        assertEquals(1, navigator.openedGroups.size)
        assertEquals("two", navigator.openedGroups.single().displayName)
        assertTrue("must be the current group value, not the stale one", navigator.openedGroups.single().files.single().modified)
    }

    fun testReorderKeysResolveToCurrentGroups() {
        val navigator = RecordingNavigator()
        val panel = panel(navigator)
        panel.render(model())
        val keys = groupInfos(panel).map { it.`object`!! }.reversed()
        panel.groupsReorderedForTest(keys)
        assertEquals(listOf("two", "one"), navigator.reordered.single().map { it.displayName })
    }

    fun testFileReorderReportsTheActiveGroupAndItsFilesInNewOrder() {
        val navigator = RecordingNavigator()
        val panel = panel(navigator)
        panel.render(model())
        val keys = fileInfos(panel).map { it.`object`!! }.reversed()
        panel.filesReorderedForTest(keys)
        val (group, files) = navigator.reorderedFiles.single()
        assertEquals("one", group.displayName)
        assertEquals(listOf(b, a), files)
    }

    fun testFileReorderIgnoresKeysThatAreNotInTheActiveGroup() {
        val navigator = RecordingNavigator()
        val panel = panel(navigator)
        panel.render(model())
        panel.filesReorderedForTest(listOf(c, b, a)) // c belongs to "two", the header shows "one"
        assertEquals(listOf(b, a), navigator.reorderedFiles.single().second)
    }

    fun testFileStripAllowsDragging() {
        val panel = panel()
        panel.render(model())
        assertTrue(panel.stripsForTest().last().isDraggingEnabledForTest())
    }

    fun testCloseGroupResolvesToCurrentGroup() {
        val navigator = RecordingNavigator()
        val panel = panel(navigator)
        panel.render(model())
        val key = groupInfos(panel)[1].`object`!!
        panel.groupCloseForTest(key, DataContext.EMPTY_CONTEXT)
        assertEquals("two", navigator.closedGroups.single().displayName)
    }
}
