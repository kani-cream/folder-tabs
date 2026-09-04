package com.github.kanicream.foldertabs.order

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

/**
 * Project-level persisted file order per directory group (design section 7.2): one entry per
 * directory order key, holding that group's file VFS URLs leftmost first. Stored in the
 * workspace file because it is per-user UI state, not shared project config.
 */
@Service(Service.Level.PROJECT)
@State(name = "FolderTabsFileOrder", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class FileOrderState : PersistentStateComponent<FileOrderState.State> {

    class Entry {
        var directory: String = ""
        var files: MutableList<String> = mutableListOf()
    }

    class State {
        var entries: MutableList<Entry> = mutableListOf()
    }

    private var orders: Map<String, List<String>> = emptyMap()

    /** Directory order key to file URLs; iteration order = oldest reordered directory first. */
    val saved: Map<String, List<String>> get() = orders

    fun update(transform: (Map<String, List<String>>) -> Map<String, List<String>>) {
        orders = FileOrder.sanitize(transform(orders))
    }

    override fun getState(): State = State().also { state ->
        state.entries = orders.map { (directory, files) ->
            Entry().also { it.directory = directory; it.files = files.toMutableList() }
        }.toMutableList()
    }

    override fun loadState(state: State) {
        // Never trust persisted input; a duplicated directory keeps its first entry.
        val raw = state.entries.fold(emptyMap<String, List<String>>()) { acc, entry ->
            if (entry.directory in acc) acc else acc + (entry.directory to entry.files.toList())
        }
        orders = FileOrder.sanitize(raw)
    }

    companion object {
        fun getInstance(project: Project): FileOrderState = project.getService(FileOrderState::class.java)
    }
}
