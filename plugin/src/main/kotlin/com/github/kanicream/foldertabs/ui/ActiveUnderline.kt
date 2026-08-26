package com.github.kanicream.foldertabs.ui

import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.rd.fill2DRect
import com.intellij.openapi.rd.fill2DRoundRect
import com.intellij.ui.tabs.JBTabs
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Paints the editor's *active* selection underline (design section 4.3) over a [JBTabs] strip.
 *
 * JBTabs decides between the active (blue) and inactive (grey) underline with an internal check
 * ("is the focus owner inside the tabs?") that can never hold for a header: the editor is not a
 * descendant of the strip, so the strip's selected tab always got the grey look. Overriding that
 * check is internal API, so instead this wrapper repaints the underline of the selected tab with
 * the active colour after JBTabs has painted, using only public API: the label's bounds
 * ([JBTabs.getTabLabel]) and the editor-tab theme values ([JBUI.CurrentTheme.EditorTabs]) that the
 * platform's own painter reads. The geometry mirrors the platform's `JBEditorTabPainter` for tabs
 * on top: a rounded bar of `underlineHeight` at the bottom of the label. In the new UI the underline
 * colour is the only visible difference between an active and an inactive selected tab.
 *
 * Like JBTabs, nothing is painted for a single tab (JBTabs draws no selection then either), and the
 * underline follows [isActive] so split editors keep highlighting only the focused pane.
 */
class ActiveUnderline(
    private val tabs: JBTabs,
    private val isActive: () -> Boolean,
) : JPanel(BorderLayout()) {

    init {
        isOpaque = false
        add(tabs.component, BorderLayout.CENTER)
    }

    override fun paint(g: Graphics) {
        super.paint(g)
        val rect = underlineRectangle() ?: return
        val g2 = g.create() as Graphics2D
        try {
            val arc = JBUI.CurrentTheme.EditorTabs.underlineArc()
            if (arc > 0) g2.fill2DRoundRect(rect, arc.toDouble(), color()) else g2.fill2DRect(rect, color())
        } finally {
            g2.dispose()
        }
    }

    /** Where the active underline goes, in this panel's coordinates; null when nothing should be painted. */
    fun underlineRectangle(): Rectangle? {
        if (!isActive() || tabs.tabs.size <= MIN_TABS_FOR_SELECTION) return null
        val selected = tabs.selectedInfo ?: return null
        val label = tabs.getTabLabel(selected) ?: return null
        if (!label.isShowing && label.width <= 0) return null
        val bounds = SwingUtilities.convertRectangle(label.parent ?: return null, label.bounds, this)
        if (bounds.isEmpty) return null
        val height = JBUI.CurrentTheme.EditorTabs.underlineHeight()
        return Rectangle(bounds.x, bounds.y + bounds.height - height, bounds.width, height)
    }

    companion object {
        /** JBTabs paints the selected-tab look only when more than one tab is visible. */
        private const val MIN_TABS_FOR_SELECTION = 1

        /** The active underline colour, resolved the way the platform's editor tab theme does. */
        fun color(): Color =
            EditorColorsManager.getInstance().globalScheme.getColor(EditorColors.TAB_UNDERLINE)
                ?: JBUI.CurrentTheme.EditorTabs.underlineColor()
    }
}
