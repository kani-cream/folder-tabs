package com.github.kanicream.foldertabs.ui

import com.github.kanicream.foldertabs.FolderTabsBundle
import com.github.kanicream.foldertabs.NoopFolderTabsNavigator
import com.github.kanicream.foldertabs.actions.ToggleCollapsedAction
import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.github.kanicream.foldertabs.model.FileTabModel
import com.github.kanicream.foldertabs.model.GroupedTabsModel
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

/** Issue #26 / design section 4.1.3: a header collapses to a one-line bar and expands again. */
class GroupedTabsPanelCollapseTest : BasePlatformTestCase() {

    private class RecordingNavigator : NoopFolderTabsNavigator() {
        val collapseRequests = mutableListOf<Boolean>()
        override fun setHeadersCollapsed(collapsed: Boolean) { collapseRequests += collapsed }
    }

    private val a = LightVirtualFile("a.txt")
    private val b = LightVirtualFile("b.txt")
    private val dir = LightVirtualFile("one")

    private fun model(modified: VirtualFile? = null) = GroupedTabsModel(
        listOf(DirectoryGroupModel(dir, "one", "/one", listOf(a, b).map { FileTabModel(it, it.name, "/one/${it.name}", modified = it == modified) })),
    )

    private fun panel(navigator: FolderTabsNavigator = RecordingNavigator()): GroupedTabsPanel =
        GroupedTabsPanel(project, a, navigator).also { Disposer.register(testRootDisposable, it) }

    private fun shows(panel: GroupedTabsPanel, part: JComponent): Boolean = SwingUtilities.isDescendingFrom(part, panel.component)

    private fun stripsShown(panel: GroupedTabsPanel) = panel.stripsForTest().all { shows(panel, it.component) }

    fun testExpandedByDefault() {
        val panel = panel()
        panel.render(model())
        assertFalse(panel.isCollapsed)
        assertTrue(stripsShown(panel))
        assertFalse(shows(panel, panel.collapsedBarForTest().component))
    }

    fun testCollapsingReplacesTheStripsWithAOneLineBar() {
        val panel = panel()
        panel.render(model())
        panel.setCollapsed(true)
        assertTrue(panel.isCollapsed)
        assertFalse(stripsShown(panel))
        assertTrue(shows(panel, panel.collapsedBarForTest().component))
        assertEquals("one › a.txt", panel.collapsedBarForTest().text)
    }

    fun testTheBarFollowsRendersAndTheModifiedMarker() {
        val panel = panel()
        panel.setCollapsed(true)
        panel.render(model(modified = a))
        assertEquals("one › *a.txt", panel.collapsedBarForTest().text)
        panel.updateModified(a, false)
        assertEquals("one › a.txt", panel.collapsedBarForTest().text)
    }

    fun testAFileWithoutAGroupShowsOnlyItsName() {
        val panel = panel()
        panel.setCollapsed(true)
        panel.render(GroupedTabsModel.EMPTY)
        assertEquals("a.txt", panel.collapsedBarForTest().text)
    }

    fun testExpandingBringsTheStripsBack() {
        val panel = panel()
        panel.render(model())
        panel.setCollapsed(true)
        panel.setCollapsed(false)
        assertFalse(panel.isCollapsed)
        assertTrue(stripsShown(panel))
        assertFalse(shows(panel, panel.collapsedBarForTest().component))
    }

    /** The label covers the bar and owns a tooltip, so it is the component the clicks really land on. */
    fun testAClickOnTheBarsLabelAsksToExpand() {
        val navigator = RecordingNavigator()
        val panel = panel(navigator)
        panel.render(model())
        panel.setCollapsed(true)
        click(panel.collapsedBarForTest().labelForTest())
        assertEquals(listOf(false), navigator.collapseRequests)
    }

    fun testAClickOnTheBarsPaddingAsksToExpandToo() {
        val navigator = RecordingNavigator()
        val panel = panel(navigator)
        panel.setCollapsed(true)
        click(panel.collapsedBarForTest().component)
        assertEquals(listOf(false), navigator.collapseRequests)
    }

    fun testARightClickDoesNotExpand() {
        val navigator = RecordingNavigator()
        val panel = panel(navigator)
        panel.setCollapsed(true)
        click(panel.collapsedBarForTest().labelForTest(), button = MouseEvent.BUTTON3, popupTrigger = true)
        assertEmpty(navigator.collapseRequests)
    }

    /** A real click as the platform's ClickListener sees it: press and release on the same spot. */
    private fun click(target: Component, button: Int = MouseEvent.BUTTON1, popupTrigger: Boolean = false) {
        target.setSize(200, 20)
        val modifiers = if (button == MouseEvent.BUTTON1) MouseEvent.BUTTON1_DOWN_MASK else MouseEvent.BUTTON3_DOWN_MASK
        for (id in listOf(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED)) {
            target.dispatchEvent(MouseEvent(target, id, System.currentTimeMillis(), modifiers, 5, 5, 1, popupTrigger, button))
        }
    }

    fun testTheBarTellsHowToExpand() {
        val panel = panel()
        panel.setCollapsed(true)
        assertEquals(FolderTabsBundle.message("header.expand"), panel.collapsedBarForTest().component.toolTipText)
    }

    fun testTheTabPopupOffersTheCollapseToggle() {
        val panel = panel()
        panel.render(model())
        for (strip in panel.stripsForTest()) {
            val group = checkNotNull(strip.popupGroupForTest())
            val ids = group.getChildren(null).map { ActionManager.getInstance().getId(it) }
            assertTrue("$ids", ToggleCollapsedAction.ID in ids)
        }
    }
}
