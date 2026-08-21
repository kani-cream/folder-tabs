package com.github.kanicream.foldertabs.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import java.awt.AWTEvent
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

/**
 * One single-row tab strip backed by the platform's editor-style [JBTabs]
 * (design section 11.3: created through [JBTabsFactory.createEditorTabs], never `new`).
 *
 * The strip is a pure view: [render] brings its tabs in line with the given [Item]s and marks
 * the selected one; only user-initiated selection changes / drags reach [onSelect] / [onReorder].
 * Programmatic changes during [render] are suppressed via [syncing].
 *
 * Rendering is diff-based: when the key sequence is unchanged the existing [TabInfo]s are
 * updated in place. Rebuilding tabs while JBTabs' own drag is in progress would pull the
 * dragged TabInfo from under it (NPE in SingleRowLayout), so a rebuild that arrives while the
 * mouse is down is deferred until the button is actually released. The release is observed
 * application-wide (JBTabs' drag routes it through the glass pane, not the tab label); a timer
 * only steps in when no drag activity has been seen for [pointerIdleTimeoutMs], so a long drag
 * never triggers a rebuild while the strip never stays frozen if a release got lost.
 */
class TabStrip(
    project: Project,
    parentDisposable: Disposable,
    private val onSelect: (key: Any) -> Unit,
    private val onReorder: ((keysInNewOrder: List<Any>) -> Unit)? = null,
    private val pointerIdleTimeoutMs: Int = POINTER_IDLE_TIMEOUT_MS,
) {

    data class Item(val key: Any, val text: String, val tooltip: String, val icon: Icon? = null)

    private val tabs: JBTabs = JBTabsFactory.createEditorTabs(project, parentDisposable).apply {
        presentation
            .setSingleRow(true)
            .setTabDraggingEnabled(onReorder != null) // design 7.1: JBTabs' own DnD, no custom handling
            .setPaintFocus(false)
            .setSupportsCompression(true)
        addListener(object : TabsListener {
            override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
                if (syncing) return
                newSelection?.`object`?.let(onSelect)
            }

            override fun tabsMoved() {
                if (syncing) return
                onReorder?.invoke(getTabs().mapNotNull { it.`object` })
            }
        }, parentDisposable)
        addTabMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = onPointerDown()
            override fun mouseReleased(e: MouseEvent) = onPointerUp()
        })
    }

    val component: JComponent get() = tabs.component

    private var syncing = false
    private var pointerDown = false
    private var deferred: Pair<List<Item>, Any?>? = null

    /** `System.nanoTime()` of the last MOUSE_DRAGGED seen while the pointer is down; `null` = none. */
    private var lastDragNanos: Long? = null

    /** Sees releases/drags wherever they land (glass pane, other windows); installed only while pressed. */
    private val globalMouseListener = AWTEventListener { event ->
        when ((event as? MouseEvent)?.id) {
            MouseEvent.MOUSE_DRAGGED -> lastDragNanos = System.nanoTime()
            MouseEvent.MOUSE_RELEASED -> onPointerUp()
        }
    }

    /** Safety net for a lost release: fires after [pointerIdleTimeoutMs] without drag activity. */
    private val pointerTimeout = Timer(pointerIdleTimeoutMs.coerceAtLeast(1)) { onPointerTimeout() }.apply { isRepeats = false }

    init {
        Disposer.register(parentDisposable, Disposable { stopWatchingPointer() })
    }

    fun render(items: List<Item>, selectedKey: Any?) {
        val current = tabs.tabs
        if (current.map { it.`object` } == items.map { it.key }) {
            updateInPlace(current, items, selectedKey)
            return
        }
        if (pointerDown) {
            deferred = items to selectedKey
            return
        }
        rebuild(items, selectedKey)
    }

    /** Cheap in-place update (used for the modified indicator) without rebuilding the strip. */
    fun updateText(key: Any, text: String) {
        tabs.tabs.firstOrNull { it.`object` == key }?.setText(text)
    }

    private fun updateInPlace(current: List<TabInfo>, items: List<Item>, selectedKey: Any?) {
        syncing = true
        try {
            current.zip(items).forEach { (info, item) -> apply(info, item) }
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

    private fun apply(info: TabInfo, item: Item): TabInfo = info
        .setText(item.text)
        .setTooltipText(HtmlChunk.text(item.tooltip))
        .setIcon(item.icon)
        .setObject(item.key)

    private fun onPointerDown() {
        if (!pointerDown) {
            Toolkit.getDefaultToolkit().addAWTEventListener(globalMouseListener, MOUSE_EVENT_MASKS)
        }
        pointerDown = true
        lastDragNanos = null
        pointerTimeout.restart()
    }

    private fun onPointerTimeout() {
        if (!pointerDown) return
        val idleMs = lastDragNanos?.let { (System.nanoTime() - it) / NANOS_PER_MILLI } ?: Long.MAX_VALUE
        if (idleMs < pointerIdleTimeoutMs) {
            pointerTimeout.restart() // a drag is still in progress: keep waiting for the real release
            return
        }
        onPointerUp()
    }

    private fun onPointerUp() {
        stopWatchingPointer()
        if (!pointerDown) return
        pointerDown = false
        deferred?.let { (items, selected) ->
            deferred = null
            rebuild(items, selected)
        }
    }

    private fun stopWatchingPointer() {
        pointerTimeout.stop()
        Toolkit.getDefaultToolkit().removeAWTEventListener(globalMouseListener)
    }

    /** Tabs are navigation only; the content area stays empty and takes no space. */
    private fun emptyContent(): JComponent = JPanel().apply {
        preferredSize = Dimension(0, 0)
        minimumSize = Dimension(0, 0)
        isOpaque = false
    }

    /** Test hook: the live TabInfos, to prove in-place updates keep instances. */
    internal fun tabInfosForTest(): List<TabInfo> = tabs.tabs

    /** Test hook: what the safety-net timer does when it fires. */
    internal fun firePointerTimeoutForTest() = onPointerTimeout()

    private companion object {
        const val POINTER_IDLE_TIMEOUT_MS = 3_000
        const val NANOS_PER_MILLI = 1_000_000L
        const val MOUSE_EVENT_MASKS: Long = AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK
    }
}
