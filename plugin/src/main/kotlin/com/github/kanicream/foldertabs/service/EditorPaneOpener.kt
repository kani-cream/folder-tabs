package com.github.kanicream.foldertabs.service

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiManager
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

/**
 * Opens a file in the split pane a header belongs to (design section 13, issue #29).
 *
 * The platform's open calls have no pane parameter: they open into the splitters' *current* window,
 * and that window follows the keyboard focus (the splitters' focus watcher updates it on every
 * focus-gained inside an editor window). A header click, however, reaches us before JBTabs moves
 * the focus (it requests the tab's focusable component *later*), so opening right away still
 * targets the previously focused pane. The opener therefore moves the focus into the
 * header's pane itself and opens on the next EDT turn, once that focus change has been processed.
 * With no pane, or when the pane already owns the focus, the file opens right away as before.
 *
 * Stable Public API Only: no `FileEditorManagerEx` / `EditorWindow`; the pane is only ever a
 * component the focus is sent to.
 */
class EditorPaneOpener(
    private val project: Project,
    private val open: (VirtualFile) -> Unit = { openInCurrentPane(project, it) },
    private val isFocusInside: (JComponent) -> Boolean = { UIUtil.isFocusAncestor(it) },
    private val requestFocus: (JComponent) -> Unit = { IdeFocusManager.getInstance(project).requestFocus(it, true) },
    private val afterFocusSettles: (Runnable) -> Unit = { runnable ->
        ApplicationManager.getApplication().invokeLater(runnable, ModalityState.defaultModalityState(), project.disposed)
    },
) {

    /** Opens [file]; in [pane] (the header's editor component) when one is given and showing. */
    fun open(file: VirtualFile, pane: JComponent?) {
        if (!file.isValid) return
        if (pane == null || isFocusInside(pane)) {
            open(file)
            return
        }
        requestFocus(pane)
        afterFocusSettles(
            Runnable {
                if (project.isDisposed || !file.isValid) return@Runnable
                open(file)
            },
        )
    }

    companion object {
        /**
         * The default open, replacing `FileEditorManager.openFile(file, true)` (deprecated in
         * 2026.3, design section 27.2). `EditorHelper` goes straight to the editor manager; the
         * descriptor navigation path unfolds the caret line, so it is only the fallback for a
         * file without PSI.
         */
        private fun openInCurrentPane(project: Project, file: VirtualFile) {
            val psiFile = runReadActionBlocking { PsiManager.getInstance(project).findFile(file) }
            val opened = psiFile != null && EditorHelper.openInEditor(psiFile, false, true) != null
            if (!opened) openRequest(project, file).navigateInEditor(project, true)
        }

        internal fun openRequest(project: Project, file: VirtualFile): OpenFileDescriptor =
            OpenFileDescriptor(project, file).setUseCurrentWindow(true)
    }
}
