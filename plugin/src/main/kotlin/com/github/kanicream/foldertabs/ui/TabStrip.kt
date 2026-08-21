package com.github.kanicream.foldertabs.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import java.awt.Dimension
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
 * mouse is down is deferred until release.
 */
class TabStrip(
    project: Project,
    parentDisposable: Disposable,
    private val onSelect: (key: Any) -> Unit,
    private val onReorder: ((keysInNewOrder: List<Any>) -> Unit)? = null,
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

    /** Safety net: a release delivered elsewhere (glass pane) must not leave the strip frozen. */
    private val pointerTimeout = Timer(POINTER_TIMEOUT_MS) { onPointerUp() }.apply { isRepeats = false }

    init {
        Disposer.register(parentDisposable, Disposable { pointerTimeout.stop() })
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
        pointerDown = true
        pointerTimeout.restart()
    }

    private fun onPointerUp() {
        pointerTimeout.stop()
        if (!pointerDown) return
        pointerDown = false
        deferred?.let { (items, selected) ->
            deferred = null
            rebuild(items, selected)
        }
    }

    /** Tabs are navigation only; the content area stays empty and takes no space. */
    private fun emptyContent(): JComponent = JPanel().apply {
        preferredSize = Dimension(0, 0)
        minimumSize = Dimension(0, 0)
        isOpaque = false
    }

    /** Test hook: the live TabInfos, to prove in-place updates keep instances. */
    internal fun tabInfosForTest(): List<TabInfo> = tabs.tabs

    private companion object {
        const val POINTER_TIMEOUT_MS = 3_000
    }
}
