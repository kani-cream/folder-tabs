package com.github.kanicream.foldertabs.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.JBTabsEx
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * One single-row tab strip backed by the platform's editor-style [JBTabs]
 * (design section 11.3: created through [JBTabsFactory.createEditorTabs], never `new`).
 *
 * Selected-tab look: JBTabs itself always paints this strip's selected tab in its *inactive*
 * colours (its active check needs the focus owner inside the tabs, and the editor is not a
 * descendant of a header). [ActiveUnderline] therefore paints the active underline over the
 * selected tab whenever the owner says the strip [isActive]; see that class for the details.
 *
 * Focus: JBTabs moves the focus into the clicked tab's content (`select(info, requestFocus = true)`).
 * The standard editor tabs hold the editor itself there, so a click lands in the editor; this
 * strip's content is an empty, non-focusable panel, and every tab names the owner's [focusTarget]
 * (the pane's editor) as its preferred focusable component instead. Without that a click pulled the
 * focus out of the editor into the header, which made the header "inactive" and dropped its underline.
 *
 * The strip is a pure view: [render] brings its tabs in line with the given [Item]s and marks
 * the selected one; only user clicks / drags reach [onSelect] / [onReorder]. Programmatic
 * changes during [render] are suppressed via [syncing]. With a [Close] configuration the strip's
 * right-click menu (JBTabs' own popup, so its Select Next / Previous Tab entries stay) gets one
 * close entry for the right-clicked tab ([JBTabs.getTargetInfo]), and if [Close.showButton] every
 * tab shows the standard editor-tab close button ([TabInfo.setTabLabelActions]) (design section
 * 15); both only report the tab's key plus the click's [DataContext].
 *
 * Navigation happens on the *click* (button release without a drag), not when JBTabs selects
 * the pressed tab: navigating on press switches the editor under the mouse, hides this strip,
 * and leaves both our press state and JBTabs' DragHelper press state dangling (no release ever
 * reaches a hidden strip) — which is what made drags of non-active tabs fail and left stale
 * tab orders on other headers. JBTabs still highlights the pressed tab, which is exactly what its
 * drag code expects (it moves the *selected* tab's label).
 *
 * Rendering is diff-based: when the key sequence is unchanged the existing [TabInfo]s are
 * updated in place.
 *
 * Drag safety (design 7.1): JBTabs' DragHelper keeps the dragged TabInfo and moves the label of
 * the *selected* tab; rebuilding the strip or moving the selection while it drags corrupts the
 * layout (NPE in SingleRowLayout, ghost tabs). Mouse events cannot tell us when a drag ends —
 * the IDE glass pane consumes MOUSE_DRAGGED / MOUSE_RELEASED during a drag, so neither the tab
 * label nor a Toolkit listener sees them. JBTabs reports its drag explicitly through
 * [TabInfo.DragDelegate] ([dragStarted] / [dragFinishedOrCanceled]); every tab gets one, and
 * while a drag or a plain press is in progress rebuilds and selection moves are deferred and
 * flushed once the interaction ends. A timer is only a safety net for a lost release.
 */
class TabStrip(
    project: Project,
    parentDisposable: Disposable,
    private val onSelect: (key: Any) -> Unit,
    private val onReorder: ((keysInNewOrder: List<Any>) -> Unit)? = null,
    private val close: Close? = null,
    private val isActive: () -> Boolean = { true },
    private val focusTarget: () -> JComponent? = { null },
) {

    data class Item(val key: Any, val text: String, val tooltip: String, val icon: Icon? = null)

    /** How this strip's tabs close: the handler, the menu entry text, and whether tabs show the inline button. */
    class Close(val onClose: (key: Any, context: DataContext) -> Unit, val menuText: String, val showButton: Boolean)

    private val tabs: JBTabs = JBTabsFactory.createEditorTabs(project, parentDisposable).apply {
        presentation
            .setSingleRow(true)
            .setTabDraggingEnabled(onReorder != null) // design 7.1: JBTabs' own DnD, no custom handling
            .setTabLabelActionsAutoHide(false) // like the editor tabs: the close button is always visible
            .setPaintFocus(false)
            .setSupportsCompression(true)
        addListener(object : TabsListener {
            override fun tabsMoved() {
                if (syncing) return
                onReorder?.invoke(getTabs().mapNotNull { it.`object` })
            }
        }, parentDisposable)
        addTabMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) onPointerDown()
            }

            override fun mouseReleased(e: MouseEvent) = onPointerUp(e)
        })
    }

    /** JBTabs calls this when it opens its tab popup, after it recorded the right-clicked tab. */
    private val popupGroupSupplier = java.util.function.Supplier<ActionGroup> {
        val close = close
        val key = tabs.targetInfo?.`object`
        if (close == null || key == null) DefaultActionGroup() else closeGroup(key, close)
    }

    private val underline = object : ActiveUnderline(tabs, isActive) {
        override fun addNotify() {
            super.addNotify()
            onAttachedToWindow()
        }
    }

    /** The strip's Swing component: the JBTabs wrapped in its [ActiveUnderline] overlay. */
    val component: JComponent get() = underline

    private var syncing = false
    private var disposed = false

    /** Mouse button down on one of our tab labels (cleared by the label's release or [dragging] end). */
    private var pressed = false

    /** JBTabs' DragHelper is dragging one of our tabs (between the delegate callbacks). */
    private var dragging = false

    /** Last render that arrived during an interaction; flushed when the interaction ends. */
    private var deferred: Pair<List<Item>, Any?>? = null

    private val interacting: Boolean get() = pressed || dragging

    /** Installed on every [TabInfo] so JBTabs tells us when its drag starts and ends. */
    private val dragDelegate = object : TabInfo.DragDelegate {
        override fun dragStarted(mouseEvent: MouseEvent) {
            dragging = true
            interactionTimeout.restart()
        }

        override fun dragFinishedOrCanceled() {
            // Called from DragHelper.endDrag() before it clears its own state: finish the interaction
            // on the next EDT turn so the flush never runs inside JBTabs' drag handling.
            SwingUtilities.invokeLater { if (!disposed) endInteraction() }
        }
    }

    /** Safety net only: a press whose release never reached us must not freeze the strip. */
    private val interactionTimeout = Timer(INTERACTION_TIMEOUT_MS) { endInteraction() }.apply { isRepeats = false }

    init {
        // JBTabs' own popup (keeps its Select Next / Previous Tab entries); our entry is added per target tab.
        if (close != null) tabs.setPopupGroup(popupGroupSupplier, ActionPlaces.EDITOR_TAB_POPUP, true)
        Disposer.register(parentDisposable, Disposable {
            disposed = true
            interactionTimeout.stop()
        })
    }

    fun render(items: List<Item>, selectedKey: Any?) {
        val current = tabs.tabs
        val sameKeys = current.map { it.`object` } == items.map { it.key }
        if (interacting) {
            // Text / icon updates are safe during a drag; structure and selection wait for the flush.
            deferred = items to selectedKey
            if (sameKeys) updateInPlace(current, items, selectedKey, applySelection = false)
            return
        }
        if (sameKeys) updateInPlace(current, items, selectedKey, applySelection = true) else rebuild(items, selectedKey)
    }

    /**
     * First-layout stability (issue #13). Headers are rendered while their editor is still hidden
     * (a non-selected editor tab has no root pane), and JBTabs sizes the tab-label actions (the close
     * button) only when it can see a root pane: rendered detached, the buttons stay at width 0. On the
     * first show the strip was laid out without them and jumped once they appeared on the next update.
     * Refreshing the actions the moment the strip enters a window makes the first layout the final one.
     */
    private fun onAttachedToWindow() {
        (tabs as? JBTabsEx)?.updateTabActions(true)
    }

    /** Repaints the strip after the owner's active state may have changed (focus moved). */
    fun repaintActiveState() {
        underline.repaint()
    }

    /** Cheap in-place update (used for the modified indicator) without rebuilding the strip. */
    fun updateText(key: Any, text: String) {
        tabs.tabs.firstOrNull { it.`object` == key }?.setText(text)
    }

    private fun updateInPlace(current: List<TabInfo>, items: List<Item>, selectedKey: Any?, applySelection: Boolean) {
        syncing = true
        try {
            current.zip(items).forEach { (info, item) -> apply(info, item) }
            if (!applySelection) return
            current.firstOrNull { it.`object` == selectedKey }
                ?.takeIf { it != tabs.selectedInfo }
                ?.let { tabs.select(it, false) }
        } finally {
            syncing = false
        }
    }

    private fun rebuild(items: List<Item>, selectedKey: Any?) {
        syncing = true
        try {
            tabs.removeAllTabs()
            val infos = items.map { apply(TabInfo(emptyContent()), it) }
            infos.forEach { tabs.addTab(it) }
            infos.firstOrNull { it.`object` == selectedKey }?.let { tabs.select(it, false) }
        } finally {
            syncing = false
        }
    }

    private fun apply(info: TabInfo, item: Item): TabInfo {
        // The close button is bound to the key: install it once per key, keep it across text updates.
        if (close != null && close.showButton && (info.tabLabelActions == null || info.`object` != item.key)) {
            info.setTabLabelActions(closeGroup(item.key, close), ActionPlaces.EDITOR_TAB)
        }
        return info
            .setText(item.text)
            .setTooltipText(HtmlChunk.text(item.tooltip))
            .setIcon(item.icon)
            .setObject(item.key)
            .setPreferredFocusableComponent(focusTarget())
            .also { it.dragDelegate = dragDelegate }
    }

    private fun closeGroup(key: Any, close: Close): ActionGroup =
        DefaultActionGroup(CloseTabAction(key, close.menuText, close.onClose))

    private fun onPointerDown() {
        pressed = true
        interactionTimeout.restart()
    }

    /**
     * A plain click's release (during a drag the glass pane consumes it and [dragDelegate] ends the
     * interaction instead). A left-button click on a tab navigates to it.
     */
    private fun onPointerUp(e: MouseEvent) {
        if (dragging) return
        val wasPressed = pressed
        endInteraction()
        if (!wasPressed || !SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger) return
        // The tab under the pointer; JBTabs already selected the pressed tab, so fall back to that
        // (e.g. before the first layout pass, when hit-testing has no geometry).
        val info = tabs.findInfo(e) ?: tabs.selectedInfo
        info?.`object`?.let(onSelect)
    }

    private fun endInteraction() {
        interactionTimeout.stop()
        pressed = false
        dragging = false
        deferred?.let { (items, selected) ->
            deferred = null
            render(items, selected)
        }
    }

    /** Tabs are navigation only; the content area stays empty, takes no space and never takes the focus. */
    private fun emptyContent(): JComponent = JPanel().apply {
        preferredSize = Dimension(0, 0)
        minimumSize = Dimension(0, 0)
        isOpaque = false
        isFocusable = false
    }

    /** Test hook: the live TabInfos, to prove in-place updates keep instances. */
    internal fun tabInfosForTest(): List<TabInfo> = tabs.tabs

    /** Test hook: the underlying JBTabs. */
    internal fun tabsForTest(): JBTabs = tabs

    /** Test hook: what [ActiveUnderline.addNotify] does (headless tests never get a real addNotify). */
    internal fun onAttachedToWindowForTest() = onAttachedToWindow()

    /** Test hook: whether the owner currently reports the strip as active. */
    internal fun isActiveForTest(): Boolean = isActive()

    /** Test hook: JBTabs' current selection. */
    internal fun selectedInfoForTest(): TabInfo? = tabs.selectedInfo

    /** Test hook: what the safety-net timer does when it fires. */
    internal fun fireInteractionTimeoutForTest() = endInteraction()

    /** Test hook: the group JBTabs would show in its popup right now (null when closing is off). */
    internal fun popupGroupForTest(): ActionGroup? = if (close == null) null else popupGroupSupplier.get()

    private companion object {
        /** Long enough that no real press or drag hits it; it only frees a strip whose release was lost. */
        const val INTERACTION_TIMEOUT_MS = 10_000
    }
}
