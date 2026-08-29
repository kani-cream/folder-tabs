package com.github.kanicream.foldertabs.ui

import com.github.kanicream.foldertabs.FolderTabsBundle
import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.github.kanicream.foldertabs.model.FileTabModel
import com.github.kanicream.foldertabs.model.GroupedTabsModel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBPanel
import com.intellij.util.IconUtil
import java.awt.BorderLayout
import java.awt.KeyboardFocusManager
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JComponent

/**
 * The two-row header added above one [com.intellij.openapi.fileEditor.FileEditor]
 * (design sections 4, 11): Directory Group Tabs on top, File Tabs of the active group below.
 *
 * A header belongs to exactly one file ([ownFile]): the editor it sits on always shows that
 * file, so the header's active group/file are derived from it, not from global selection.
 * The header is a pure projection of the [GroupedTabsModel] handed to [render].
 *
 * Selected tabs are painted like the standard editor tabs: with the *active* colours only while
 * the header's editor has focus ([isEditorActive]; in split editors only the focused pane's
 * selected tab is blue). Clicking a tab sends the focus to that editor ([editorFocusTarget]),
 * exactly like the standard tabs, so the header never becomes the focus owner itself. Focus changes reach the strips through a
 * [KeyboardFocusManager] listener so they repaint, as EditorTabs does on its own focus.
 */
class GroupedTabsPanel(
    private val project: Project,
    private val ownFile: VirtualFile,
    private val navigator: FolderTabsNavigator,
    private val isEditorActive: () -> Boolean = { true },
    private val editorFocusTarget: () -> JComponent? = { null },
) : Disposable {

    private val groupTabs = TabStrip(
        project, this, onSelect = ::onGroupSelected, onReorder = ::onGroupsReordered,
        close = TabStrip.Close(::onGroupClose, FolderTabsBundle.message("group.close"), showButton = false),
        isActive = isEditorActive, focusTarget = editorFocusTarget,
    )
    private val fileTabs = TabStrip(
        project, this, onSelect = ::onFileSelected,
        close = TabStrip.Close(::onFileClose, FolderTabsBundle.message("tab.close"), showButton = true),
        isActive = isEditorActive, focusTarget = editorFocusTarget,
    )

    private val focusListener = PropertyChangeListener {
        groupTabs.repaintActiveState()
        fileTabs.repaintActiveState()
    }

    init {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(FOCUS_OWNER_PROPERTY, focusListener)
    }

    val component: JComponent = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        add(groupTabs.component, BorderLayout.NORTH)
        add(fileTabs.component, BorderLayout.SOUTH)
    }

    /** Currently rendered group of [ownFile]; null until the first render finds it. */
    var activeGroup: DirectoryGroupModel? = null
        private set

    /** The model of the last [render]; group tab keys are resolved against it (see [groupFor]). */
    private var rendered: GroupedTabsModel = GroupedTabsModel.EMPTY

    fun render(model: GroupedTabsModel) {
        val group = model.groupOf(ownFile)
        activeGroup = group
        rendered = model

        // Group tabs are keyed by the group's stable identity (issue #16): keying them by the whole
        // DirectoryGroupModel value made any file's modified flip a "new key" and rebuilt the strip.
        groupTabs.render(
            items = model.groups.map { TabStrip.Item(key = it.orderKey, text = it.displayName, tooltip = it.fullPath, icon = AllIcons.Nodes.Folder) },
            selectedKey = group?.orderKey,
        )
        fileTabs.render(
            items = group?.files.orEmpty().map { TabStrip.Item(key = it.file, text = fileText(it), tooltip = it.fullPath, icon = fileIcon(it.file)) },
            selectedKey = ownFile,
        )
        // Tabs were added after the header joined the editor: make the editor re-layout.
        component.revalidate()
        component.repaint()
    }

    /** Targeted update for the modified indicator (design section 19): no strip rebuild. */
    fun updateModified(file: VirtualFile, modified: Boolean) {
        val tab = activeGroup?.files?.firstOrNull { it.file == file } ?: return
        fileTabs.updateText(file, fileText(tab.copy(modified = modified)))
    }

    /** Same icon the standard editor tabs show: file type plus read-status overlays, resolved lazily. */
    private fun fileIcon(file: VirtualFile): Icon? =
        if (file.isValid) IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, project) else null

    private fun fileText(tab: FileTabModel): String =
        if (tab.modified) MODIFIED_PREFIX + tab.displayName else tab.displayName

    /** The current model's group behind a group tab key ([DirectoryGroupModel.orderKey]). */
    private fun groupFor(key: Any): DirectoryGroupModel? = rendered.groups.firstOrNull { it.orderKey == key }

    private fun onGroupSelected(key: Any) {
        val group = groupFor(key) ?: return
        if (group == activeGroup) return
        navigator.openGroup(group)
    }

    private fun onGroupsReordered(keys: List<Any>) {
        navigator.reorderGroups(keys.mapNotNull(::groupFor))
    }

    private fun onFileSelected(key: Any) {
        val file = key as? VirtualFile ?: return
        if (file == ownFile) return
        navigator.openFile(file)
    }

    private fun onFileClose(key: Any, context: DataContext) {
        val file = key as? VirtualFile ?: return
        navigator.closeFile(file, context)
    }

    private fun onGroupClose(key: Any, context: DataContext) {
        val group = groupFor(key) ?: return
        navigator.closeGroup(group, context)
    }

    /** Test hook: what JBTabs' drag-reorder reports (the tab keys in their new order). */
    internal fun groupsReorderedForTest(keysInNewOrder: List<Any>) = onGroupsReordered(keysInNewOrder)

    /** Test hook: the group tab's close entry. */
    internal fun groupCloseForTest(key: Any, context: DataContext) = onGroupClose(key, context)

    /** Test hook: the header's strips. */
    internal fun stripsForTest(): List<TabStrip> = listOf(groupTabs, fileTabs)

    /** Test hook: the focus listener this header registered on the [KeyboardFocusManager]. */
    internal fun focusListenerForTest(): PropertyChangeListener = focusListener

    override fun dispose() {
        // TabStrips are registered as children of this Disposable; only the focus listener is ours.
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(FOCUS_OWNER_PROPERTY, focusListener)
    }

    companion object {
        /** [KeyboardFocusManager] property that changes whenever keyboard focus settles on a component. */
        const val FOCUS_OWNER_PROPERTY: String = "permanentFocusOwner"

        /** Same convention as the IDE's "Mark modified (*)" editor-tab option (design section 4.1.2). */
        const val MODIFIED_PREFIX: String = "*"
    }
}
