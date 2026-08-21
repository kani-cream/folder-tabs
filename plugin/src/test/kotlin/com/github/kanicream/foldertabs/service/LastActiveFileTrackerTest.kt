package com.github.kanicream.foldertabs.service

import com.github.kanicream.foldertabs.model.DirectoryGroupModel
import com.github.kanicream.foldertabs.model.FileTabModel
import com.intellij.testFramework.LightVirtualFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastActiveFileTrackerTest {

    // LightVirtualFile has no parent, so all files land in the "Other" group (directory = null).
    private val a = LightVirtualFile("a.go")
    private val b = LightVirtualFile("b.go")
    private val c = LightVirtualFile("c.go")

    private fun group(vararg files: LightVirtualFile) = DirectoryGroupModel(
        directory = null,
        displayName = "Other",
        fullPath = "Other",
        files = files.map { FileTabModel(it, it.name, it.name, modified = false) },
    )

    @Test
    fun `without history the first file in sort order is the target`() {
        assertEquals(a, LastActiveFileTracker().targetFor(group(a, b, c)))
    }

    @Test
    fun `remembered file wins over first`() {
        val tracker = LastActiveFileTracker()
        tracker.remember(c)
        assertEquals(c, tracker.targetFor(group(a, b, c)))
    }

    @Test
    fun `remembered file that is no longer open falls back to first`() {
        val tracker = LastActiveFileTracker()
        tracker.remember(c)
        assertEquals(a, tracker.targetFor(group(a, b)))
    }

    @Test
    fun `forget drops the history`() {
        val tracker = LastActiveFileTracker()
        tracker.remember(c)
        tracker.forget(c)
        assertEquals(a, tracker.targetFor(group(a, b, c)))
    }

    @Test
    fun `empty group has no target`() {
        assertNull(LastActiveFileTracker().targetFor(group()))
    }
}
