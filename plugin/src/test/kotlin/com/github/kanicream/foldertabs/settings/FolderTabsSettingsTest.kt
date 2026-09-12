package com.github.kanicream.foldertabs.settings

import com.github.kanicream.foldertabs.grouping.GroupLabelPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Test

/** Design section 21: persisted settings are sanitized on the way in, and the state is replaced, never mutated. */
class FolderTabsSettingsTest {

    @Test
    fun `loadState falls back to the default depth for a value outside the offered choices`() {
        val settings = FolderTabsSettings()
        settings.loadState(FolderTabsSettings.State(enabled = false, groupLabelDepth = 99))
        assertEquals(GroupLabelPolicy.DEFAULT_DEPTH, settings.groupLabelDepth)
        assertFalse("other fields survive", settings.enabled)
    }

    @Test
    fun `loadState keeps an offered depth`() {
        val settings = FolderTabsSettings()
        settings.loadState(FolderTabsSettings.State(groupLabelDepth = GroupLabelPolicy.PROJECT_ROOT_DEPTH))
        assertEquals(GroupLabelPolicy.PROJECT_ROOT_DEPTH, settings.groupLabelDepth)
    }

    @Test
    fun `setters sanitize and replace the state object`() {
        val settings = FolderTabsSettings()
        val before = settings.state
        settings.groupLabelDepth = -5
        assertEquals(GroupLabelPolicy.DEFAULT_DEPTH, settings.groupLabelDepth)
        settings.enabled = false
        assertNotSame(before, settings.state)
        assertEquals("the state handed out earlier still holds the defaults", FolderTabsSettings.State(), before)
    }
}
