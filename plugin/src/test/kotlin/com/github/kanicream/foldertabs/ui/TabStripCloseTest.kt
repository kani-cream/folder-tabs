package com.github.kanicream.foldertabs.ui

import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import javax.swing.JRootPane

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

    fun testMenuOnlyCloseHasNoButtonButStillAPopupEntry() {
        val disposable = Disposer.newDisposable()
        try {
            val closed = mutableListOf<Any>()
            val strip = TabStrip(project, disposable, onSelect = {}, close = closeSupport(showButton = false) { closed += it })
            strip.render(listOf(item("g")), selectedKey = "g")
            assertNull(strip.tabInfosForTest()[0].tabLabelActions)

            val group = checkNotNull(strip.popupGroupForTest())
            perform(group.getChildren(null).filterIsInstance<CloseTabAction>().single())
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

    fun testPopupOffersCloseForTheTargetTabAndKeepsJBTabsNavigationEntries() {
        val disposable = Disposer.newDisposable()
        try {
            val closed = mutableListOf<Any>()
            val strip = TabStrip(project, disposable, onSelect = {}, close = closeSupport { closed += it })
            strip.render(listOf(item("a"), item("b")), selectedKey = "b")

            // JBTabs' target is the right-clicked tab (or, outside a popup, the selected one).
            val group = checkNotNull(strip.popupGroupForTest())
            perform(group.getChildren(null).filterIsInstance<CloseTabAction>().single())
            assertEquals(listOf<Any>("b"), closed)
            // The popup is JBTabs' own (setPopupGroup with the navigation group kept), not a private menu.
            assertTrue(strip.tabInfosForTest().isNotEmpty())
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testCloseActionUpdatesOnEdtSoJBTabsBuildsAButtonForIt() {
        // JBTabs' ActionPanel only creates buttons for EDT actions; a BGT action gets no button at all.
        val action = CloseTabAction("k", "Close") { _, _ -> }
        assertEquals(ActionUpdateThread.EDT, action.actionUpdateThread)
    }

    fun testNoPopupEntriesWithoutACloseHandler() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {})
            strip.render(listOf(item("a")), selectedKey = "a")
            assertNull(strip.popupGroupForTest())
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testRightClickDoesNotNavigate() {
        val disposable = Disposer.newDisposable()
        try {
            val selected = mutableListOf<Any>()
            val strip = TabStrip(project, disposable, onSelect = { selected += it }, close = closeSupport {})
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            val labelB = tabLabels(strip).elementAt(1)
            labelB.dispatchEvent(MouseEvent(labelB, MouseEvent.MOUSE_PRESSED, 0L, MouseEvent.BUTTON3_DOWN_MASK, 1, 1, 1, true, MouseEvent.BUTTON3))
            labelB.dispatchEvent(MouseEvent(labelB, MouseEvent.MOUSE_RELEASED, 0L, 0, 1, 1, 1, false, MouseEvent.BUTTON3))
            assertEquals(emptyList<Any>(), selected)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    // ---- issue #13: the close button must be part of the first layout once the strip is in a window ----

    fun testCloseButtonIsSizedAsSoonAsTheStripEntersAWindow() {
        val disposable = Disposer.newDisposable()
        try {
            // Rendered while detached (like a header on a not-yet-shown editor): JBTabs cannot size the
            // button because there is no root pane yet.
            val strip = TabStrip(project, disposable, onSelect = {}, close = closeSupport { })
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            val label = strip.tabsForTest().let { it.getTabLabel(it.tabs.first())!! }
            val detachedWidth = label.preferredSize.width

            // Entering a window: the hook must refresh the actions so the first layout is final.
            val rootPane = JRootPane()
            rootPane.contentPane.add(strip.component)
            strip.onAttachedToWindowForTest()

            assertTrue("close button should widen the label: $detachedWidth -> ${label.preferredSize.width}",
                label.preferredSize.width > detachedWidth)
        } finally {
            Disposer.dispose(disposable)
        }
    }
}
