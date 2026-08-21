package com.github.kanicream.foldertabs.order

/**
 * Pure rules for the user-defined group order (design section 7.1). Entries are directory
 * VFS URLs; the list is ordered, first = leftmost tab.
 */
object GroupOrder {

    /** Upper bound on remembered entries (design section 7.1). */
    const val DEFAULT_CAP: Int = 200

    /**
     * Items whose URL is in [savedUrls] come first in saved order; the rest keep their
     * incoming (default) order.
     */
    fun <T> sort(items: List<T>, urlOf: (T) -> String?, savedUrls: List<String>): List<T> {
        val rank = savedUrls.withIndex().associate { (i, url) -> url to i }
        val (pinned, rest) = items.partition { urlOf(it) in rank }
        return pinned.sortedBy { rank.getValue(urlOf(it)!!) } + rest
    }

    /**
     * Merges a user drag result into the saved list: visible entries are re-placed into the
     * slots they already occupied (so hidden entries keep their positions); visible entries
     * that were not saved yet are appended. Hidden entries beyond [cap] are dropped oldest-first.
     */
    fun applyReorder(saved: List<String>, visibleNewOrder: List<String>, cap: Int = DEFAULT_CAP): List<String> {
        val visible = visibleNewOrder.toSet()
        val knownVisible = visibleNewOrder.filter { it in saved }
        val newEntries = visibleNewOrder.filter { it !in saved }
        val replacements = knownVisible.iterator()
        val merged = saved.map { if (it in visible) replacements.next() else it } + newEntries
        return trimToCap(merged, visible, cap)
    }

    /** Rewrites [oldUrl] (and everything under it) to [newUrl] after a rename or move. */
    fun rename(saved: List<String>, oldUrl: String, newUrl: String): List<String> =
        saved.map { url ->
            when {
                url == oldUrl -> newUrl
                url.startsWith("$oldUrl/") -> newUrl + url.removePrefix(oldUrl)
                else -> url
            }
        }.distinct()

    /** Drops [url] and everything under it after a delete. */
    fun remove(saved: List<String>, url: String): List<String> =
        saved.filterNot { it == url || it.startsWith("$url/") }

    private fun trimToCap(entries: List<String>, keep: Set<String>, cap: Int): List<String> {
        if (entries.size <= cap) return entries
        val excess = entries.size - cap
        val droppable = entries.filter { it !in keep }.take(excess).toSet()
        return entries.filter { it !in droppable }
    }
}
