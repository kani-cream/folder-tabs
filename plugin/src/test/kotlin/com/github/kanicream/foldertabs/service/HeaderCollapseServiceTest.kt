package com.github.kanicream.foldertabs.service

import com.github.kanicream.foldertabs.FolderTabsPlatformTestCase
import com.github.kanicream.foldertabs.settings.FolderTabsSettings

/** Issue #26 / design section 4.1.3: the collapsed state is per project, runtime only, and loses nothing. */
class HeaderCollapseServiceTest : FolderTabsPlatformTestCase() {

    override fun tearDown() {
        try {
            service.setHeadersCollapsed(false)
        } finally {
            super.tearDown()
        }
    }

    fun testCollapsingAppliesToExistingAndLaterHeaders() {
        val a = open("users/a.go")
        service.setHeadersCollapsed(true)
        assertTrue(service.headersCollapsed)
        assertTrue(service.panelForTest(editorOf(a)).isCollapsed)
        val b = open("orders/b.go") // opened while collapsed
        assertTrue(service.panelForTest(editorOf(b)).isCollapsed)
        service.setHeadersCollapsed(false)
        assertFalse(service.panelForTest(editorOf(a)).isCollapsed)
        assertFalse(service.panelForTest(editorOf(b)).isCollapsed)
    }

    fun testModelAndOrderKeepFollowingWhileCollapsed() {
        open("users/a.go")
        open("orders/b.go")
        service.setHeadersCollapsed(true)
        val c = open("api/c.go")
        val groups = service.model.groups
        service.reorderGroups(listOf(groups[2], groups[0], groups[1])) // users, api, orders
        flush()
        service.setHeadersCollapsed(false)
        assertEquals(listOf("users", "api", "orders"), service.panelForTest(editorOf(c)).renderedModel.groups.map { it.displayName })
    }

    fun testCollapsedIsNotASetting() {
        val before = FolderTabsSettings.getInstance().state.copy()
        open("users/a.go")
        service.setHeadersCollapsed(true)
        assertEquals(before, FolderTabsSettings.getInstance().state)
        assertTrue("headers stay attached while collapsed", service.headerCount >= 1)
    }

    /** Turning the feature off in Settings also forgets the collapse: it comes back expanded. */
    fun testDisablingThePluginResetsTheCollapse() {
        val settings = FolderTabsSettings.getInstance()
        val a = open("users/a.go")
        service.setHeadersCollapsed(true)
        settings.enabled = false
        service.applySettings()
        assertFalse(service.headersCollapsed)
        settings.enabled = true
        service.applySettings()
        flush()
        assertFalse(service.panelForTest(editorOf(a)).isCollapsed)
    }

    fun testSettingTheSameStateTwiceIsANoop() {
        val a = open("users/a.go")
        service.setHeadersCollapsed(true)
        service.setHeadersCollapsed(true)
        assertTrue(service.panelForTest(editorOf(a)).isCollapsed)
    }
}
