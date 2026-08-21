package com.github.kanicream.foldertabs.order

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

/**
 * Project-level persisted group order (design section 7.1): directory VFS URLs, leftmost first.
 * Stored in the workspace file because it is per-user UI state, not shared project config.
 */
@Service(Service.Level.PROJECT)
@State(name = "FolderTabsGroupOrder", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class GroupOrderState : PersistentStateComponent<GroupOrderState.State> {

    class State {
        var urls: MutableList<String> = mutableListOf()
    }

    private var urls: List<String> = emptyList()

    val savedUrls: List<String> get() = urls

    fun update(transform: (List<String>) -> List<String>) {
        urls = transform(urls).take(GroupOrder.DEFAULT_CAP)
    }

    override fun getState(): State = State().also { it.urls = urls.toMutableList() }

    override fun loadState(state: State) {
        // Never trust persisted input: drop blanks and duplicates, cap the size.
        urls = state.urls.filter { it.isNotBlank() }.distinct().take(GroupOrder.DEFAULT_CAP)
    }

    companion object {
        fun getInstance(project: Project): GroupOrderState = project.getService(GroupOrderState::class.java)
    }
}
