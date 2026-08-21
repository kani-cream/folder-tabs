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
     * Merges a user drag result into the saved list. The dragged (visible) order always wins:
     * visible entries are re-placed into the slots that already-saved visible entries occupied,
     * so hidden (closed) entries keep their positions, and entries that were not saved yet stay
     * next to the visible neighbour they were dropped beside. If no visible entry was saved
     * before, the whole dragged order is appended. Hidden entries beyond [cap] are dropped
     * oldest-first.
     */
    fun applyReorder(saved: List<String>, visibleNewOrder: List<String>, cap: Int = DEFAULT_CAP): List<String> {
        val visible = visibleNewOrder.toSet()
        val segments = segmentsAroundSavedEntries(saved.toSet(), visibleNewOrder)
        if (segments.isEmpty()) return trimToCap(saved + visibleNewOrder, visible, cap)
        val replacements = segments.iterator()
        val merged = saved.flatMap { if (it in visible) replacements.next() else listOf(it) }
        return trimToCap(merged, visible, cap)
    }

    /**
     * Splits the dragged order into one segment per already-saved entry: the new entries dragged
     * in front of it, then the entry itself; trailing new entries join the last segment.
     * Empty when none of the dragged entries was saved before.
     */
    private fun segmentsAroundSavedEntries(saved: Set<String>, visibleNewOrder: List<String>): List<List<String>> {
        val (segments, trailing) = visibleNewOrder.fold(emptyList<List<String>>() to emptyList<String>()) { (done, pending), url ->
            if (url in saved) (done + listOf(pending + url)) to emptyList() else done to (pending + url)
        }
        if (segments.isEmpty()) return emptyList()
        return segments.dropLast(1) + listOf(segments.last() + trailing)
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
