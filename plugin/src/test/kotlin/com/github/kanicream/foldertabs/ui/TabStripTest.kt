package com.github.kanicream.foldertabs.ui

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

/** The strip must not rebuild TabInfos when only content changes (drag safety, design 7.1). */
class TabStripTest : BasePlatformTestCase() {

    private fun item(key: String, text: String = key) = TabStrip.Item(key, text, "/$key")

    fun testSameKeysAreUpdatedInPlace() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {})
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            val before = strip.tabInfosForTest()
            strip.render(listOf(item("a", "*a"), item("b")), selectedKey = "b")
            val after = strip.tabInfosForTest()
            assertEquals(before, after) // same instances, same order
            assertEquals("*a", after[0].text)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testDifferentKeysRebuild() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {})
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            strip.render(listOf(item("b"), item("a")), selectedKey = "a")
            assertEquals(listOf("b", "a"), strip.tabInfosForTest().map { it.`object` })
        } finally {
            Disposer.dispose(disposable)
        }
    }

    // ---- drag / press safety: never rebuild or move the selection while JBTabs is dragging ----

    private fun tabLabel(strip: TabStrip): Component =
        descendants(strip.component).first { it.javaClass.simpleName == "TabLabel" }

    private fun descendants(c: Component): Sequence<Component> = sequence {
        yield(c)
        if (c is Container) c.components.forEach { yieldAll(descendants(it)) }
    }

    private fun mouse(target: Component, id: Int): MouseEvent =
        MouseEvent(target, id, 0L, MouseEvent.BUTTON1_DOWN_MASK, 1, 1, 1, false, MouseEvent.BUTTON1)

    private fun keys(strip: TabStrip) = strip.tabInfosForTest().map { it.`object` }

    fun testRebuildWaitsForJBTabsDragToFinish() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {}, onReorder = {})
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            val before = strip.tabInfosForTest()
            val delegate = before[0].dragDelegate!! // installed on every tab so JBTabs reports its drag to us

            delegate.dragStarted(mouse(strip.component, MouseEvent.MOUSE_DRAGGED))
            strip.render(listOf(item("b"), item("a")), selectedKey = "a")
            assertEquals(before, strip.tabInfosForTest()) // untouched while JBTabs holds the dragged TabInfo

            delegate.dragFinishedOrCanceled()
            UIUtil.dispatchAllInvocationEvents()
            assertEquals(listOf("b", "a"), keys(strip))
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testSelectionIsNotMovedWhileDraggingButCatchesUpAfterwards() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {}, onReorder = {})
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            val delegate = strip.tabInfosForTest()[1].dragDelegate!!

            delegate.dragStarted(mouse(strip.component, MouseEvent.MOUSE_DRAGGED))
            strip.render(listOf(item("a", "*a"), item("b")), selectedKey = "b")
            assertEquals("a", strip.tabInfosForTest().first { it == strip.selectedInfoForTest() }.`object`)
            assertEquals("*a", strip.tabInfosForTest()[0].text) // cheap in-place text updates still apply

            delegate.dragFinishedOrCanceled()
            UIUtil.dispatchAllInvocationEvents()
            assertEquals("b", strip.selectedInfoForTest()?.`object`)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testRebuildWaitsForMouseReleaseOnAPlainPress() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {}, onReorder = {})
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            val before = strip.tabInfosForTest()
            val label = tabLabel(strip)

            label.dispatchEvent(mouse(label, MouseEvent.MOUSE_PRESSED))
            strip.render(listOf(item("b"), item("a")), selectedKey = "a")
            assertEquals(before, strip.tabInfosForTest())

            label.dispatchEvent(mouse(label, MouseEvent.MOUSE_RELEASED))
            UIUtil.dispatchAllInvocationEvents()
            assertEquals(listOf("b", "a"), keys(strip))
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testPressSafetyTimeoutUnfreezesAStripWhoseReleaseGotLost() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {}, onReorder = {})
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            val label = tabLabel(strip)
            label.dispatchEvent(mouse(label, MouseEvent.MOUSE_PRESSED))
            strip.render(listOf(item("b"), item("a")), selectedKey = "a")

            strip.fireInteractionTimeoutForTest()
            UIUtil.dispatchAllInvocationEvents()
            assertEquals(listOf("b", "a"), keys(strip))
        } finally {
            Disposer.dispose(disposable)
        }
    }

    // ---- navigation happens on click (release without drag), never on press -------------------

    fun testClickNavigatesOnReleaseNotOnPress() {
        val disposable = Disposer.newDisposable()
        try {
            val selected = mutableListOf<Any>()
            val strip = TabStrip(project, disposable, onSelect = { selected += it }, onReorder = {})
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            val labelB = descendants(strip.component).filter { it.javaClass.simpleName == "TabLabel" }.elementAt(1)

            labelB.dispatchEvent(mouse(labelB, MouseEvent.MOUSE_PRESSED))
            assertEquals(emptyList<Any>(), selected) // JBTabs may highlight b, but we do not navigate yet

            labelB.dispatchEvent(mouse(labelB, MouseEvent.MOUSE_RELEASED))
            assertEquals(listOf<Any>("b"), selected)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testDragDoesNotNavigate() {
        val disposable = Disposer.newDisposable()
        try {
            val selected = mutableListOf<Any>()
            val strip = TabStrip(project, disposable, onSelect = { selected += it }, onReorder = {})
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            val labelB = descendants(strip.component).filter { it.javaClass.simpleName == "TabLabel" }.elementAt(1)
            val delegate = strip.tabInfosForTest()[1].dragDelegate!!

            labelB.dispatchEvent(mouse(labelB, MouseEvent.MOUSE_PRESSED))
            delegate.dragStarted(mouse(strip.component, MouseEvent.MOUSE_DRAGGED))
            delegate.dragFinishedOrCanceled() // the glass pane consumed the release; JBTabs tells us the drag ended
            UIUtil.dispatchAllInvocationEvents()

            assertEquals(emptyList<Any>(), selected)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    // ---- selected-tab colours: the strip paints the editor's active underline itself (public API only) ----

    fun testActiveStripPaintsTheActiveUnderlineUnderTheSelectedTab() {
        val disposable = Disposer.newDisposable()
        try {
            var active = true
            val strip = TabStrip(project, disposable, onSelect = {}, isActive = { active })
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            assertEquals(ActiveUnderline.color().rgb, underlinePixel(strip, "a"))
            active = false
            assertFalse(ActiveUnderline.color().rgb == underlinePixel(strip, "a"))
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testStripIsActiveByDefaultAndOnlyTheSelectedTabIsUnderlined() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {})
            strip.render(listOf(item("a"), item("b")), selectedKey = "b")
            assertEquals(ActiveUnderline.color().rgb, underlinePixel(strip, "b"))
            assertFalse(ActiveUnderline.color().rgb == underlinePixel(strip, "a"))
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testSingleTabGetsNoUnderlineLikeJBTabs() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {})
            strip.render(listOf(item("a")), selectedKey = "a")
            assertFalse(ActiveUnderline.color().rgb == underlinePixel(strip, "a"))
        } finally {
            Disposer.dispose(disposable)
        }
    }

    /** Lays the strip out at a fixed size, paints it, and samples the middle of the given tab's underline. */
    private fun underlinePixel(strip: TabStrip, key: String): Int {
        val component = strip.component
        component.setSize(600, 40)
        descendants(component).forEach { (it as? Container)?.doLayout() }
        val tabs = strip.tabsForTest()
        val label = tabs.getTabLabel(tabs.tabs.first { it.`object` == key })!!
        val bounds = SwingUtilities.convertRectangle(label.parent, label.bounds, component)
        assertTrue("tab label must have a size after layout: $bounds", bounds.width > 0 && bounds.height > 0)
        val image = BufferedImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().let { g -> component.paint(g); g.dispose() }
        return image.getRGB(bounds.x + bounds.width / 2, bounds.y + bounds.height - 1)
    }
}
