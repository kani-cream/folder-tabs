package com.github.kanicream.foldertabs.editor

import com.github.kanicream.foldertabs.service.GroupedTabsProjectService
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

/**
 * Project-level listener on [FileEditorManagerListener.FILE_EDITOR_MANAGER] (design section 9).
 * Registered declaratively in plugin.xml; only forwards to the project service.
 */
class FolderTabsEditorListener : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (source.project.isDisposed) return
        GroupedTabsProjectService.getInstance(source.project).onFileOpened(file)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (source.project.isDisposed) return
        GroupedTabsProjectService.getInstance(source.project).onFileClosed(file)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val project = event.manager.project
        if (project.isDisposed) return
        GroupedTabsProjectService.getInstance(project).onSelectionChanged(event.newFile, event.newEditor)
    }
}
