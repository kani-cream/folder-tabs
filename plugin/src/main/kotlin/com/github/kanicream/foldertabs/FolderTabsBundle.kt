package com.github.kanicream.foldertabs

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.FolderTabsBundle"

/** Message bundle for user-visible strings. */
object FolderTabsBundle : DynamicBundle(FolderTabsBundle::class.java, BUNDLE) {
    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        getMessage(key, *params)
}
