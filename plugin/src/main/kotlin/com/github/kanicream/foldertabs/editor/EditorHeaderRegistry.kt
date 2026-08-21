package com.github.kanicream.foldertabs.editor

import com.github.kanicream.foldertabs.ui.GroupedTabsPanel
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vfs.VirtualFile

/**
 * Tracks which [FileEditor] already carries a [GroupedTabsPanel] so a header is never
 * added twice (design section 11.2). Keys compare by identity: FileEditor implementations
 * do not override `equals`.
 *
 * Immutable-style: every change replaces the backing map. EDT only.
 */
class EditorHeaderRegistry {

    private var panels: Map<FileEditor, GroupedTabsPanel> = emptyMap()

    val size: Int get() = panels.size

    fun contains(editor: FileEditor): Boolean = panels.containsKey(editor)

    fun register(editor: FileEditor, panel: GroupedTabsPanel) {
        panels = panels + (editor to panel)
    }

    fun unregister(editor: FileEditor): GroupedTabsPanel? {
        val panel = panels[editor] ?: return null
        panels = panels - editor
        return panel
    }

    fun all(): List<Pair<FileEditor, GroupedTabsPanel>> = panels.map { it.key to it.value }

    fun editorsFor(file: VirtualFile): List<FileEditor> = panels.keys.filter { it.file == file }
}
