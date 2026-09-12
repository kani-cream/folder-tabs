package com.github.kanicream.foldertabs.ui

import com.github.kanicream.foldertabs.FolderTabsBundle
import com.intellij.icons.AllIcons
import com.intellij.ui.ClickListener
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.JComponent

/**
 * The one-line stand-in for a collapsed header (design section 4.1.3, issue #26): an expand
 * chevron and "group › file" for the editor it sits on, so the user still sees where they are.
 * A left click anywhere on the bar asks the owner to expand the headers again. Pure view: the
 * owner feeds it text through [update].
 *
 * The click is detected with the platform's [ClickListener] (press and release on the same spot,
 * primary button, no popup trigger), installed on the label as well as on the panel: a component
 * with a tooltip registers its own mouse listener, which makes it the event target, so a click on
 * the text would never reach the panel otherwise.
 */
class CollapsedHeaderBar(private val onExpand: () -> Unit) {

    private val label = JBLabel("", AllIcons.General.ChevronDown, JBLabel.LEADING).apply {
        toolTipText = FolderTabsBundle.message("header.expand")
    }

    val component: JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        border = JBUI.Borders.empty(VERTICAL_PADDING, HORIZONTAL_PADDING)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = FolderTabsBundle.message("header.expand")
        isFocusable = false
        add(label, BorderLayout.CENTER)
    }

    private val clickListener = object : ClickListener() {
        override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
            onExpand()
            return true
        }
    }

    init {
        clickListener.installOn(component)
        clickListener.installOn(label)
    }

    /** What the bar currently says. */
    val text: String get() = label.text

    fun update(groupName: String?, fileText: String) {
        label.text = if (groupName == null) fileText else "$groupName$SEPARATOR$fileText"
    }

    /** Test hook: the label that covers the bar and receives the clicks. */
    internal fun labelForTest(): JComponent = label

    companion object {
        const val SEPARATOR: String = " › "
        private const val VERTICAL_PADDING = 2
        private const val HORIZONTAL_PADDING = 8
    }
}
