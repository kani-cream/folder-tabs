package com.github.kanicream.foldertabs.order

/**
 * Pure rules for the user-defined file order inside each directory group (design section 7.2).
 * The map is keyed by [com.github.kanicream.foldertabs.model.DirectoryGroupModel.orderKey]; each
 * value is that group's file VFS URLs, leftmost first, with exactly the [GroupOrder] semantics.
 *
 * Map iteration order is significant: the most recently reordered directory is last, so
 * [directoryCap] drops the oldest directories first.
 */
object FileOrder {

    /** Upper bound on remembered directories (design section 7.2). */
    const val DEFAULT_DIRECTORY_CAP: Int = 200

    /** Upper bound on remembered files per directory; same rule as [GroupOrder.DEFAULT_CAP]. */
    const val DEFAULT_FILE_CAP: Int = GroupOrder.DEFAULT_CAP

    /** The saved order of [directoryKey], or `null` when the user never reordered that group. */
    fun savedFor(saved: Map<String, List<String>>, directoryKey: String): List<String>? = saved[directoryKey]

    /**
     * Merges a drag result into [directoryKey]'s entry with [GroupOrder.applyReorder] and moves that
     * entry to the newest position. Directories beyond [directoryCap] are dropped oldest-first.
     */
    fun applyReorder(
        saved: Map<String, List<String>>,
        directoryKey: String,
        visibleNewOrder: List<String>,
        directoryCap: Int = DEFAULT_DIRECTORY_CAP,
        fileCap: Int = DEFAULT_FILE_CAP,
    ): Map<String, List<String>> {
        if (visibleNewOrder.isEmpty()) return saved
        val merged = GroupOrder.applyReorder(saved[directoryKey].orEmpty(), visibleNewOrder, fileCap)
        val others = saved.filterKeys { it != directoryKey }
        return trimToDirectoryCap(others + (directoryKey to merged), directoryCap)
    }

    /**
     * Rewrites [oldUrl] (and everything under it) to [newUrl] in directory keys and file URLs after a
     * rename or move. When the rewritten key collides with an existing entry, the existing entry wins.
     */
    fun rename(saved: Map<String, List<String>>, oldUrl: String, newUrl: String): Map<String, List<String>> =
        saved.entries.fold(emptyMap()) { acc, (key, files) ->
            val newKey = GroupOrder.rename(listOf(key), oldUrl, newUrl).single()
            if (newKey != key && newKey in saved) acc else acc + (newKey to GroupOrder.rename(files, oldUrl, newUrl))
        }

    /** Drops [url] and everything under it, from directory keys and file lists; empty entries vanish. */
    fun remove(saved: Map<String, List<String>>, url: String): Map<String, List<String>> =
        saved.filterKeys { GroupOrder.remove(listOf(it), url).isNotEmpty() }
            .mapValues { (_, files) -> GroupOrder.remove(files, url) }
            .filterValues { it.isNotEmpty() }

    /** Never trust persisted input: drops blank keys / URLs, duplicates, empty entries; applies both caps. */
    fun sanitize(
        raw: Map<String, List<String>>,
        directoryCap: Int = DEFAULT_DIRECTORY_CAP,
        fileCap: Int = DEFAULT_FILE_CAP,
    ): Map<String, List<String>> {
        val cleaned = raw.filterKeys { it.isNotBlank() }
            .mapValues { (_, files) -> files.filter { it.isNotBlank() }.distinct().take(fileCap) }
            .filterValues { it.isNotEmpty() }
        return trimToDirectoryCap(cleaned, directoryCap)
    }

    private fun trimToDirectoryCap(entries: Map<String, List<String>>, cap: Int): Map<String, List<String>> {
        if (entries.size <= cap) return entries
        return entries.entries.drop(entries.size - cap).associate { (k, v) -> k to v }
    }
}
