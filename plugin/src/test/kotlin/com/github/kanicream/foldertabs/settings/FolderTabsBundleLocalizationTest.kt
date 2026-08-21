package com.github.kanicream.foldertabs.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.PropertyResourceBundle
import java.util.ResourceBundle

/** The Settings page is localized: every key in the default bundle has a translated `ja` value. */
class FolderTabsBundleLocalizationTest {

    @Test
    fun `ja bundle exists as its own resource`() {
        val stream = javaClass.classLoader.getResourceAsStream("messages/FolderTabsBundle_ja.properties")
        assertTrue("messages/FolderTabsBundle_ja.properties is missing", stream != null)
        stream?.close()
    }

    @Test
    fun `ja bundle has exactly the same keys as the default bundle`() {
        assertEquals(load(Locale.ROOT).keySet(), load(Locale.JAPANESE).keySet())
    }

    @Test
    fun `every ja value is translated, not a copy of the English text`() {
        val en = load(Locale.ROOT)
        val ja = load(Locale.JAPANESE)
        for (key in en.keySet() - PRODUCT_NAME_KEYS) {
            val value = ja.getString(key)
            assertTrue("$key is blank in ja", value.isNotBlank())
            assertNotEquals("$key is not translated in ja", en.getString(key), value)
        }
    }

    /**
     * Comments are rendered as HTML by the Settings UI, and `&` is the mnemonic marker in IDE
     * bundles (`&lt;` would come out as ` lt;`), so comment texts must avoid `<`, `>` and `&`.
     */
    @Test
    fun `comments contain no angle brackets or ampersands`() {
        for (locale in listOf(Locale.ROOT, Locale.JAPANESE)) {
            val bundle = load(locale)
            for (key in bundle.keySet().filter { it.endsWith(".comment") }) {
                val value = bundle.getString(key)
                assertFalse("$key ($locale) must not contain < > or &: $value", value.any { it == '<' || it == '>' || it == '&' })
            }
        }
    }

    private companion object {
        /** Product names stay as-is in every language. */
        val PRODUCT_NAME_KEYS = setOf("settings.display.name")
    }

    private fun load(locale: Locale): ResourceBundle {
        val suffix = if (locale == Locale.ROOT) "" else "_${locale.language}"
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("messages/FolderTabsBundle$suffix.properties")) {
            "bundle for $locale not found"
        }
        return stream.use { PropertyResourceBundle(it.reader(Charsets.UTF_8)) }
    }
}
