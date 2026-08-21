package com.github.kanicream.foldertabs.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.JBTabsFactory
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * One single-row tab strip backed by the platform's editor-style [JBTabs]
 * (design section 11.3: created through [JBTabsFactory.createEditorTabs], never `new`).
 *
 * The strip is a pure view: [render] replaces its tabs from [Item]s and marks the selected
 * one; only user-initiated selection changes / drags reach [onSelect] / [onReorder].
 * Programmatic changes during [render] are suppressed via [syncing].
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
    }

    val component: JComponent get() = tabs.component

    private var syncing = false

    fun render(items: List<Item>, selectedKey: Any?) {
        syncing = true
        try {
            tabs.removeAllTabs()
            val infos = items.map(::toTabInfo)
            infos.forEach { tabs.addTab(it) }
            infos.firstOrNull { it.`object` == selectedKey }?.let { tabs.select(it, false) }
        } finally {
            syncing = false
        }
    }

    /** Cheap in-place update (used for the modified indicator) without rebuilding the strip. */
    fun updateText(key: Any, text: String) {
        tabs.tabs.firstOrNull { it.`object` == key }?.setText(text)
    }

    private fun toTabInfo(item: Item): TabInfo =
        TabInfo(emptyContent())
            .setText(item.text)
            .setTooltipText(HtmlChunk.text(item.tooltip))
            .setIcon(item.icon)
            .setObject(item.key)

    /** Tabs are navigation only; the content area stays empty and takes no space. */
    private fun emptyContent(): JComponent = JPanel().apply {
        preferredSize = Dimension(0, 0)
        minimumSize = Dimension(0, 0)
        isOpaque = false
    }
}
