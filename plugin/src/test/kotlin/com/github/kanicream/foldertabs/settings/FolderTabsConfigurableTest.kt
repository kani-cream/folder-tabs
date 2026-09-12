package com.github.kanicream.foldertabs.settings

import com.github.kanicream.foldertabs.FolderTabsBundle
import com.github.kanicream.foldertabs.FolderTabsPlatformTestCase
import com.github.kanicream.foldertabs.grouping.GroupLabelPolicy
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.UIUtil
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList

/** Design section 21: the Settings page binds to the application settings and re-applies them to open projects. */
class FolderTabsConfigurableTest : FolderTabsPlatformTestCase() {

    private val settings get() = FolderTabsSettings.getInstance()
    private lateinit var configurable: FolderTabsConfigurable

    /** The one Settings page under test; the base fixture restores the settings it changed. */
    private lateinit var page: JComponent

    override fun setUp() {
        super.setUp()
        configurable = FolderTabsConfigurable()
        page = configurable.createComponent()
    }

    override fun tearDown() {
        try {
            configurable.disposeUIResources()
        } finally {
            super.tearDown()
        }
    }

    private fun checkBox(): JCheckBox = checkNotNull(UIUtil.findComponentOfType(page, JCheckBox::class.java))

    @Suppress("UNCHECKED_CAST")
    private fun depthCombo(): ComboBox<Int> = checkNotNull(UIUtil.findComponentOfType(page, ComboBox::class.java)) as ComboBox<Int>

    fun testPageStartsUnmodifiedAndMirrorsTheSettings() {
        assertEquals(settings.enabled, checkBox().isSelected)
        assertEquals(settings.groupLabelDepth, depthCombo().selectedItem)
        assertFalse(configurable.isModified)
    }

    fun testApplyingTheMasterSwitchRemovesAndRestoresTheHeaders() {
        open("users/a.go")
        assertTrue(service.headerCount >= 1)
        checkBox().isSelected = false
        assertTrue(configurable.isModified)
        configurable.apply()
        assertFalse(settings.enabled)
        assertEquals("apply reaches the open project", 0, service.headerCount)
        checkBox().isSelected = true
        configurable.apply()
        flush()
        assertTrue(settings.enabled)
        assertTrue(service.headerCount >= 1)
    }

    fun testApplyingTheDepthWritesTheSetting() {
        depthCombo().selectedItem = GroupLabelPolicy.PROJECT_ROOT_DEPTH
        configurable.apply()
        assertEquals(GroupLabelPolicy.PROJECT_ROOT_DEPTH, settings.groupLabelDepth)
    }

    fun testResetDiscardsUnappliedChanges() {
        val before = settings.enabled
        checkBox().isSelected = !before
        configurable.reset()
        assertEquals(before, checkBox().isSelected)
        assertFalse(configurable.isModified)
    }

    fun testDepthChoicesReadAsNumbersAndProjectRoot() {
        val renderer = depthCombo().renderer
        fun text(value: Int?) = (renderer.getListCellRendererComponent(JList<Int>(), value, 0, false, false) as JLabel).text
        assertEquals("3", text(3))
        assertEquals(FolderTabsBundle.message("settings.group.label.depth.project.root"), text(GroupLabelPolicy.PROJECT_ROOT_DEPTH))
        assertEquals("", text(null))
    }

    fun testNoHelpTopicSoTheIdeShowsNoHelpButton() {
        assertNull(configurable.helpTopic)
    }
}
