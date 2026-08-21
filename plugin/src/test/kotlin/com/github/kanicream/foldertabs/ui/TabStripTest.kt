package com.github.kanicream.foldertabs.ui

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import javax.swing.JPanel

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

    // ---- drag safety: a rebuild never happens while the mouse is down ---------------------

    private fun tabLabel(strip: TabStrip): Component =
        descendants(strip.component).first { it.javaClass.simpleName == "TabLabel" }

    private fun descendants(c: Component): Sequence<Component> = sequence {
        yield(c)
        if (c is Container) c.components.forEach { yieldAll(descendants(it)) }
    }

    private fun mouse(target: Component, id: Int, x: Int = 1): MouseEvent =
        MouseEvent(target, id, 0L, MouseEvent.BUTTON1_DOWN_MASK, x, 1, 1, false, MouseEvent.BUTTON1)

    fun testRebuildIsDeferredWhileMouseIsDownAndRunsOnReleaseDeliveredElsewhere() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {}, onReorder = {})
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            val before = strip.tabInfosForTest()

            tabLabel(strip).dispatchEvent(mouse(tabLabel(strip), MouseEvent.MOUSE_PRESSED))
            strip.render(listOf(item("b"), item("a")), selectedKey = "a")
            assertEquals(before, strip.tabInfosForTest()) // deferred: nothing rebuilt yet

            // The release lands on an unrelated component (e.g. the glass pane during JBTabs' drag).
            val elsewhere = JPanel()
            elsewhere.dispatchEvent(mouse(elsewhere, MouseEvent.MOUSE_RELEASED))
            assertEquals(listOf("b", "a"), strip.tabInfosForTest().map { it.`object` })
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testPointerTimeoutDoesNotRebuildWhileDragEventsKeepArriving() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {}, onReorder = {})
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            val before = strip.tabInfosForTest()

            tabLabel(strip).dispatchEvent(mouse(tabLabel(strip), MouseEvent.MOUSE_PRESSED))
            strip.render(listOf(item("b"), item("a")), selectedKey = "a")
            val elsewhere = JPanel()
            elsewhere.dispatchEvent(mouse(elsewhere, MouseEvent.MOUSE_DRAGGED, x = 40))

            strip.firePointerTimeoutForTest() // a drag is still active: must stay deferred
            assertEquals(before, strip.tabInfosForTest())

            elsewhere.dispatchEvent(mouse(elsewhere, MouseEvent.MOUSE_RELEASED, x = 40))
            assertEquals(listOf("b", "a"), strip.tabInfosForTest().map { it.`object` })
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testPointerTimeoutReleasesAFrozenStripWhenNoDragActivity() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {}, onReorder = {}, pointerIdleTimeoutMs = 0)
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            tabLabel(strip).dispatchEvent(mouse(tabLabel(strip), MouseEvent.MOUSE_PRESSED))
            strip.render(listOf(item("b"), item("a")), selectedKey = "a")

            strip.firePointerTimeoutForTest() // no drag activity since the press: safety net fires
            assertEquals(listOf("b", "a"), strip.tabInfosForTest().map { it.`object` })
        } finally {
            Disposer.dispose(disposable)
        }
    }
}
