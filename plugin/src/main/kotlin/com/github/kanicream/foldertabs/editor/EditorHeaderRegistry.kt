package com.github.kanicream.foldertabs.editor

import com.github.kanicream.foldertabs.ui.GroupedTabsPanel
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Component
import java.util.IdentityHashMap

/**
 * Tracks which [FileEditor] already carries a [GroupedTabsPanel] so a header is never
 * added twice (design section 11.2). Keys compare by identity (an [IdentityHashMap]): one
 * header per editor instance, whatever a FileEditor implementation's `equals` says.
 *
 * Also remembers which split pane each editor was last shown in (design section 13.0): the
 * pane is an opaque identity resolved by the service and only ever compared by identity.
 *
 * Immutable-style: every change replaces the backing map with a fresh copy. EDT only.
 */
class EditorHeaderRegistry {

    private var panels: Map<FileEditor, GroupedTabsPanel> = IdentityHashMap()
    private var panes: Map<FileEditor, Any> = IdentityHashMap()

    val size: Int get() = panels.size

    fun contains(editor: FileEditor): Boolean = panels.containsKey(editor)

    fun register(editor: FileEditor, panel: GroupedTabsPanel) {
        panels = IdentityHashMap(panels).apply { put(editor, panel) }
    }

    fun unregister(editor: FileEditor): GroupedTabsPanel? {
        val panel = panels[editor] ?: return null
        panels = IdentityHashMap(panels).apply { remove(editor) }
        panes = IdentityHashMap(panes).apply { remove(editor) }
        return panel
    }

    fun panelOf(editor: FileEditor): GroupedTabsPanel? = panels[editor]

    /** The registered editor whose component (or preferred focus target) is [component]. */
    fun editorOwning(component: Component): FileEditor? =
        panels.keys.firstOrNull { it.component === component || it.preferredFocusedComponent === component }

    // ---- pane attribution (design section 13, v1.3) ----

    fun attribute(editor: FileEditor, pane: Any) {
        if (!panels.containsKey(editor)) return
        panes = IdentityHashMap(panes).apply { put(editor, pane) }
    }

    fun paneOf(editor: FileEditor): Any? = panes[editor]

    fun all(): List<Pair<FileEditor, GroupedTabsPanel>> = panels.map { it.key to it.value }

    fun editorsFor(file: VirtualFile): List<FileEditor> = panels.keys.filter { it.file == file }
}
