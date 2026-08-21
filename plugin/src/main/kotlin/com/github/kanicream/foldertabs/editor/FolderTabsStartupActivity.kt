package com.github.kanicream.foldertabs.editor

import com.github.kanicream.foldertabs.service.GroupedTabsProjectService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Attaches headers to editors that were restored before the listener was active
 * (design section 20: lifecycle follows the project). Idempotent via the registry.
 */
class FolderTabsStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().invokeLater({
            if (!project.isDisposed) GroupedTabsProjectService.getInstance(project).attachAllOpenEditors()
        }, project.disposed)
    }
}
