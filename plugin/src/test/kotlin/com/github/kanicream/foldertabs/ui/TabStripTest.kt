package com.github.kanicream.foldertabs.ui

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** The strip must not rebuild TabInfos when only content changes (drag safety, design 7.1). */
class TabStripTest : BasePlatformTestCase() {

    private fun item(key: String, text: String = key) = TabStrip.Item(key, text, "/$key")

    fun testSameKeysAreUpdatedInPlace() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {})
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            val before = strip.tabInfosForTest()
            strip.render(listOf(item("a", "*a"), item("b")), selectedKey = "b")
            val after = strip.tabInfosForTest()
            assertEquals(before, after) // same instances, same order
            assertEquals("*a", after[0].text)
        } finally {
            Disposer.dispose(disposable)
        }
    }

    fun testDifferentKeysRebuild() {
        val disposable = Disposer.newDisposable()
        try {
            val strip = TabStrip(project, disposable, onSelect = {})
            strip.render(listOf(item("a"), item("b")), selectedKey = "a")
            strip.render(listOf(item("b"), item("a")), selectedKey = "a")
            assertEquals(listOf("b", "a"), strip.tabInfosForTest().map { it.`object` })
        } finally {
            Disposer.dispose(disposable)
        }
    }
}
