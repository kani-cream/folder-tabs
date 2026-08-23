package com.github.kanicream.foldertabs.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent

/** File tabs close like the standard editor tabs: an inline close button and a "Close" popup entry. */
class TabStripCloseTest : BasePlatformTestCase() {

    private fun item(key: String) = TabStrip.Item(key, key, "/$key")

    private fun closeSupport(showButton: Boolean = true, onClose: (Any) -> Unit) =
        TabStrip.Close(onClose = { key, _ -> onClose(key) }, menuText = "Close", showButton = showButton)

    private fun descendants(c: Component): Sequence<Component> = sequence {
        yield(c)
        if (c is Container) c.components.forEach { yieldAll(descendants(it)) }
    }

    private fun tabLabels(strip: TabStrip) = descendants(strip.component).filter { it.javaClass.simpleName == "TabLabel" }

    private fun closeActionOf(strip: TabStrip, index: Int): CloseTabAction {
        val group = checkNotNull(strip.tabInfosForTest()[index].tabLabelActions) { "tab $index has no label actions" }
        return group.getChildren(null).filterIsInstance<CloseTabAction>().single()
    }

    private fun perform(action: CloseTabAction) {
        action.actionPerformed(AnActionEvent.createEvent(action, DataContext.EMPTY_CONTEXT, null, "test", ActionUiKind.NONE, null))
    }

    fun testEveryTabCarriesACloseButtonWhenACloseHandlerIsGiven() {
        val disposable = Disposer.newDisposable()
        try {
            val closed = mutableListOf<Any>()
            val strip = TabStrip(project, disposable, onSelect = {}, close = closeSupport { key -> closed += key })
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")

            perform(closeActionOf(strip, 1))
            assertEquals(listOf<Any>("b"), closed)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testMenuOnlyCloseHasNoButtonButStillAPopup() {
        val disposable = Disposer.newDisposable()
        try {
            val closed = mutableListOf<Any>()
            val shown = mutableListOf<ActionGroup>()
            val strip = TabStrip(project, disposable, onSelect = {}, close = closeSupport(showButton = false) { closed += it })
            strip.popupPresenterForTest = { _, group -> shown += group }
            strip.render(listOf(item("g")), selectedKey = "g")
            assertNull(strip.tabInfosForTest()[0].tabLabelActions)

            val label = tabLabels(strip).first()
            label.dispatchEvent(MouseEvent(label, MouseEvent.MOUSE_PRESSED, 0L, MouseEvent.BUTTON3_DOWN_MASK, 1, 1, 1, true, MouseEvent.BUTTON3))
            perform(shown.single().getChildren(null).filterIsInstance<CloseTabAction>().single())
            assertEquals(listOf<Any>("g"), closed)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testNoCloseButtonWithoutACloseHandler() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {})
            strip.render(listOf(item("a")), selectedKey = "a")
            assertNull(strip.tabInfosForTest()[0].tabLabelActions)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testInPlaceUpdateKeepsTheCloseActionGroup() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {}, close = closeSupport {})
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            val before = strip.tabInfosForTest().map { it.tabLabelActions }
            strip.render(listOf(TabStrip.Item("a", "*a", "/a"), item("b")), selectedKey = "b")
            assertEquals(before, strip.tabInfosForTest().map { it.tabLabelActions })
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testRebuildGivesReusedTabInfosTheCloseActionOfTheirNewKey() {
        val disposable = Disposer.newDisposable()
        try {
            val closed = mutableListOf<Any>()
            val strip = TabStrip(project, disposable, onSelect = {}, close = closeSupport { key -> closed += key })
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            strip.render(listOf(item("b"), item("a")), selectedKey = "a")
            perform(closeActionOf(strip, 0))
            assertEquals(listOf<Any>("b"), closed)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testRightClickOffersCloseForTheTabUnderThePointer() {
        val disposable = Disposer.newDisposable()
        try {
            val closed = mutableListOf<Any>()
            val selected = mutableListOf<Any>()
            val shown = mutableListOf<ActionGroup>()
            val strip = TabStrip(project, disposable, onSelect = { selected += it }, close = closeSupport { key -> closed += key })
            strip.popupPresenterForTest = { _, group -> shown += group }
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            val labelB = tabLabels(strip).elementAt(1)

            val press = MouseEvent(labelB, MouseEvent.MOUSE_PRESSED, 0L, MouseEvent.BUTTON3_DOWN_MASK, 1, 1, 1, true, MouseEvent.BUTTON3)
            labelB.dispatchEvent(press)
            val release = MouseEvent(labelB, MouseEvent.MOUSE_RELEASED, 0L, 0, 1, 1, 1, false, MouseEvent.BUTTON3)
            labelB.dispatchEvent(release)

            assertEquals(1, shown.size)
            perform(shown.single().getChildren(null).filterIsInstance<CloseTabAction>().single())
            assertEquals(listOf<Any>("b"), closed)
            assertEquals(emptyList<Any>(), selected) // a right click never navigates
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testNoPopupWithoutACloseHandler() {
        val disposable = Disposer.newDisposable()
        try {
            val shown = mutableListOf<ActionGroup>()
            val strip = TabStrip(project, disposable, onSelect = {})
            strip.popupPresenterForTest = { _, group -> shown += group }
            strip.render(listOf(item("a")), selectedKey = "a")
            val label = tabLabels(strip).first()
            label.dispatchEvent(MouseEvent(label, MouseEvent.MOUSE_PRESSED, 0L, MouseEvent.BUTTON3_DOWN_MASK, 1, 1, 1, true, MouseEvent.BUTTON3))
            assertEquals(emptyList<ActionGroup>(), shown)
        } finally {
            Disposer.dispose(disposable)
        }
    }
}
