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
 * one; only a user-initiated selection change reaches [onSelect]. Programmatic selection
 * during [render] is suppressed via [syncing].
 */
class TabStrip(
    project: Project,
    parentDisposable: Disposable,
    private val onSelect: (key: Any) -> Unit,
) {

    data class Item(val key: Any, val text: String, val tooltip: String, val icon: Icon? = null)

    private val tabs: JBTabs = JBTabsFactory.createEditorTabs(project, parentDisposable).apply {
        presentation
            .setSingleRow(true)
            .setTabDraggingEnabled(false)
            .setPaintFocus(false)
            .setSupportsCompression(true)
        addListener(object : TabsListener {
            override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
                if (syncing) return
                newSelection?.`object`?.let(onSelect)
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
