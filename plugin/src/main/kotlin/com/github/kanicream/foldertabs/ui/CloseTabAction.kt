package com.github.kanicream.foldertabs.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAwareAction

/**
 * "Close" for one tab (design section 15): the inline close button of a file tab and the single
 * entry of a tab's right-click menu. Looks like the standard editor tab's button (same icons) and
 * only reports the wish to close; what closing means is up to [onClose].
 */
class CloseTabAction(
    private val key: Any,
    text: String,
    private val onClose: (key: Any, context: DataContext) -> Unit,
) : DumbAwareAction() {

    init {
        templatePresentation.text = text
        templatePresentation.icon = AllIcons.Actions.Close
        templatePresentation.hoveredIcon = AllIcons.Actions.CloseHovered
    }

    override fun actionPerformed(e: AnActionEvent) = onClose(key, e.dataContext)
}
