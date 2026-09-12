package com.github.kanicream.foldertabs

import com.github.kanicream.foldertabs.service.GroupedTabsProjectService
import com.github.kanicream.foldertabs.settings.FolderTabsSettings
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Shared fixture for tests that drive the project service through the real editor manager:
 * plain directory names as group labels (depth 1, restored afterwards), and helpers to open a
 * file and to let the coalesced refresh (design section 19) run.
 */
abstract class FolderTabsPlatformTestCase : BasePlatformTestCase() {

    protected val service: GroupedTabsProjectService get() = GroupedTabsProjectService.getInstance(project)
    protected val editors: FileEditorManager get() = FileEditorManager.getInstance(project)
    private var savedDepth = 0

    /** Content of the files [open] creates; a test that needs a non-empty document overrides it. */
    protected open val fileContent: String = ""

    override fun setUp() {
        super.setUp()
        savedDepth = FolderTabsSettings.getInstance().groupLabelDepth
        FolderTabsSettings.getInstance().groupLabelDepth = 1
    }

    override fun tearDown() {
        try {
            FolderTabsSettings.getInstance().groupLabelDepth = savedDepth
        } finally {
            super.tearDown()
        }
    }

    protected fun open(path: String): VirtualFile = myFixture.addFileToProject(path, fileContent).virtualFile.also {
        editors.openFile(it, true)
        flush()
    }

    /** Runs whatever the platform and the service queued on the EDT, then rebuilds the model. */
    protected fun flush() {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        service.refreshNow()
    }

    protected fun editorOf(file: VirtualFile): FileEditor = editors.getAllEditors(file).single()
}
