package com.github.kanicream.foldertabs.settings

import com.github.kanicream.foldertabs.grouping.GroupLabelPolicy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level settings (design section 21). Persisted in `folderTabs.xml`.
 *
 * [State.enabled] turns the whole feature on/off (default ON). [State.groupLabelDepth] is the number of path segments shown in a group label
 * (design section 6.5); [GroupLabelPolicy.PROJECT_ROOT_DEPTH] means "up to the project root".
 */
@Service(Service.Level.APP)
@State(name = "FolderTabsSettings", storages = [Storage("folderTabs.xml")])
class FolderTabsSettings : PersistentStateComponent<FolderTabsSettings.State> {

    data class State(
        var enabled: Boolean = true,
        var groupLabelDepth: Int = GroupLabelPolicy.DEFAULT_DEPTH,
    )

    private var state = State()

    /** Master switch (design section 21). OFF removes every header; the IDE is back to stock. */
    var enabled: Boolean
        get() = state.enabled
        set(value) {
            state = state.copy(enabled = value)
        }

    var groupLabelDepth: Int
        get() = state.groupLabelDepth
        set(value) {
            state = state.copy(groupLabelDepth = sanitizeDepth(value))
        }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state.copy(groupLabelDepth = sanitizeDepth(state.groupLabelDepth))
    }

    fun labelPolicy(projectName: String): GroupLabelPolicy = GroupLabelPolicy(groupLabelDepth, projectName)

    companion object {
        /** Choices offered in the settings UI; 0 = project root. */
        val DEPTH_CHOICES: List<Int> = listOf(1, 2, 3, 4, 5, GroupLabelPolicy.PROJECT_ROOT_DEPTH)

        fun getInstance(): FolderTabsSettings =
            ApplicationManager.getApplication().getService(FolderTabsSettings::class.java)

        /** Never trust persisted input: anything outside the offered range falls back to the default. */
        fun sanitizeDepth(value: Int): Int =
            if (value in DEPTH_CHOICES) value else GroupLabelPolicy.DEFAULT_DEPTH
    }
}
