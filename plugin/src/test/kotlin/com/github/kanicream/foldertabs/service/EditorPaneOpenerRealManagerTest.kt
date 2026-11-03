package com.github.kanicream.foldertabs.service

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.FileEditorManagerTestCase
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.PlatformTestUtil
import javax.swing.SwingConstants

/**
 * The default open against the real `FileEditorManagerImpl`, which unlike the light fixture's test
 * manager has split panes and runs the platform's open path (design sections 13 and 27.2). The
 * internal manager API here is test setup only: production code stays on the stable API.
 */
class EditorPaneOpenerRealManagerTest : FileEditorManagerTestCase() {

    private val editors: FileEditorManagerImpl get() = manager!!

    private fun file(name: String): VirtualFile = myFixture.addFileToProject(name, TEXT).virtualFile

    private fun settle() = PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    private fun openInManager(file: VirtualFile) {
        editors.openFile(file, true)
        settle()
    }

    private fun openInPane(file: VirtualFile, pane: EditorWindow) {
        editors.openFile(file, pane, FileEditorOpenOptions())
        settle()
    }

    private fun openThroughTheHeader(file: VirtualFile) {
        EditorPaneOpener(project).open(file, pane = null)
        settle()
    }

    private fun textEditor(file: VirtualFile): Editor =
        editors.getEditors(file).filterIsInstance<TextEditor>().first().editor.also(EditorTestUtil::waitForLoading)

    private fun collapse(editor: Editor): FoldRegion {
        val folding = editor.foldingModel
        lateinit var region: FoldRegion
        folding.runBatchFoldingOperation {
            region = requireNotNull(folding.addFoldRegion(0, FIRST_LINE_END, "...")).also { it.isExpanded = false }
        }
        return region
    }

    /** Two panes that both hold [a] and [b] with [b] selected in each; the second pane is current. */
    private fun twoPanesHolding(a: VirtualFile, b: VirtualFile): Pair<EditorWindow, EditorWindow> {
        openInManager(a)
        openInManager(b)
        val first = requireNotNull(editors.currentWindow)
        editors.createSplitter(SwingConstants.VERTICAL, first)
        settle()
        val second = editors.windows.first { it !== first }
        openInPane(a, second)
        openInPane(b, second)
        editors.currentWindow = second
        return first to second
    }

    fun testATabClickLeavesACollapsedRegionOnTheCaretLineCollapsed() {
        val a = file("a.txt")
        val b = file("b.txt")
        openInManager(a)
        val editor = textEditor(a)
        val region = collapse(editor)
        editor.caretModel.moveToOffset(0)
        openInManager(b)

        openThroughTheHeader(a)
        EditorTestUtil.waitForLoading(editor)
        settle()

        assertEquals(a, editors.selectedFiles.single())
        assertFalse("the fold stays collapsed", region.isExpanded)
    }

    fun testATabClickLeavesTheCaretWhereItWas() {
        val a = file("a.txt")
        val b = file("b.txt")
        openInManager(a)
        val editor = textEditor(a)
        editor.caretModel.moveToOffset(CARET_INSIDE_THE_SECOND_LINE)
        openInManager(b)

        openThroughTheHeader(a)

        assertEquals(CARET_INSIDE_THE_SECOND_LINE, editor.caretModel.offset)
    }

    fun testAFileOpenInBothPanesOpensInTheCurrentPane() {
        val a = file("a.txt")
        val b = file("b.txt")
        val (first, second) = twoPanesHolding(a, b)

        openThroughTheHeader(a)

        assertEquals("the current pane shows the file", a, second.selectedFile)
        assertEquals("the other pane keeps its selection", b, first.selectedFile)
        assertSame(second, editors.currentWindow)
    }

    fun testTheOtherPaneOfAFileOpenInBothOpensThereWhenItIsCurrent() {
        val a = file("a.txt")
        val b = file("b.txt")
        val (first, second) = twoPanesHolding(a, b)
        editors.currentWindow = first

        openThroughTheHeader(a)

        assertEquals(a, first.selectedFile)
        assertEquals(b, second.selectedFile)
        assertSame(first, editors.currentWindow)
    }

    fun testAFileOpenOnlyInTheOtherPaneIsNotDuplicated() {
        val a = file("a.txt")
        val b = file("b.txt")
        openInManager(a)
        val first = requireNotNull(editors.currentWindow)
        editors.createSplitter(SwingConstants.VERTICAL, first)
        settle()
        val second = editors.windows.first { it !== first }
        openInPane(b, second)
        second.closeFile(a)
        settle()
        editors.currentWindow = second

        openThroughTheHeader(a)

        assertEquals(listOf(b), second.fileList)
        assertEquals(1, editors.windows.count { a in it.fileList })
    }

    private fun withTabPlacementNone(body: () -> Unit) {
        val settings = UISettings.getInstance()
        val placement = settings.editorTabPlacement
        settings.editorTabPlacement = UISettings.TABS_NONE
        try {
            body()
        } finally {
            settings.editorTabPlacement = placement
        }
    }

    fun testWithTabPlacementNoneAFileOpenInBothPanesOpensInTheCurrentPane() = withTabPlacementNone {
        val a = file("a.txt")
        val b = file("b.txt")
        val (first, second) = twoPanesHolding(a, b)

        openThroughTheHeader(a)

        assertEquals(a, second.selectedFile)
        assertEquals(b, first.selectedFile)
        assertSame(second, editors.currentWindow)
    }

    /**
     * Pins a known difference from `openFile`, which would duplicate the file into the current pane
     * here (design section 27.2). A header only lists its own pane's files, so this needs a header
     * on the project-wide fallback model.
     */
    fun testWithTabPlacementNoneAFileOpenOnlyInTheOtherPaneMovesThere() = withTabPlacementNone {
        val a = file("a.txt")
        val b = file("b.txt")
        openInManager(a)
        val first = requireNotNull(editors.currentWindow)
        editors.createSplitter(SwingConstants.VERTICAL, first)
        settle()
        val second = editors.windows.first { it !== first }
        openInPane(b, second)
        second.closeFile(a)
        settle()
        editors.currentWindow = second

        openThroughTheHeader(a)

        assertSame(first, editors.currentWindow)
        assertEquals(listOf(b), second.fileList)
    }

    /** A file whose PSI has no virtual file cannot go through `EditorHelper`; it still has to open. */
    fun testAFileWhosePsiHasNoVirtualFileOpensThroughTheFallback() {
        val light = object : LightVirtualFile("light.txt", TEXT) {
            override fun shouldSkipEventSystem() = true
        }
        val psiFile = requireNotNull(com.intellij.psi.PsiManager.getInstance(project).findFile(light))
        assertNull("the fallback's trigger", psiFile.virtualFile)

        openThroughTheHeader(light)

        assertTrue(editors.isFileOpen(light))
    }

    private companion object {
        const val TEXT = "line one\nline two\n"
        const val FIRST_LINE_END = 8
        const val CARET_INSIDE_THE_SECOND_LINE = 12
    }
}
