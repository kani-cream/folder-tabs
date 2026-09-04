package com.github.kanicream.foldertabs.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

/**
 * Opens a file in the split pane a header belongs to (design section 13, issue #29).
 *
 * The platform's `openFile` has no pane parameter: it opens into the splitters' *current* window,
 * and that window follows the keyboard focus (the splitters' focus watcher updates it on every
 * focus-gained inside an editor window). A header click, however, reaches us before JBTabs moves
 * the focus (it requests the tab's focusable component *later*), so `openFile` called right away
 * still targets the previously focused pane. The opener therefore moves the focus into the
 * header's pane itself and opens on the next EDT turn, once that focus change has been processed.
 * With no pane, or when the pane already owns the focus, the file opens right away as before.
 *
 * Stable Public API Only: no `FileEditorManagerEx` / `EditorWindow`; the pane is only ever a
 * component the focus is sent to.
 */
class EditorPaneOpener(
    private val project: Project,
    private val open: (VirtualFile) -> Unit = { FileEditorManager.getInstance(project).openFile(it, true) },
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
}
