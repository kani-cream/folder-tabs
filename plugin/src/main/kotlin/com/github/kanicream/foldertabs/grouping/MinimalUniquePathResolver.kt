package com.github.kanicream.foldertabs.grouping

/**
 * Resolves display names for directories so that directories sharing a name are shown
 * with the shortest trailing path that tells them apart (design section 6).
 *
 * Pure: works on path segment lists, not on [com.intellij.openapi.vfs.VirtualFile], so it
 * is unit-testable and independent of the VFS. Segments are ordered root-first; the last
 * segment is the directory's own name.
 */
object MinimalUniquePathResolver {

    /** Label for entries whose path could not be resolved (design section 14). */
    const val FALLBACK_NAME: String = "Other"

    /** UI separator is always `/`, regardless of OS (design section 6.4). */
    const val SEPARATOR: String = "/"

    fun <K> resolve(segmentsByKey: Map<K, List<String>>): Map<K, String> {
        val resolved = segmentsByKey
            .filterValues { it.isNotEmpty() }
            .entries
            .groupBy({ it.value.last() }, { it.key to it.value })
            .flatMap { (_, sameName) -> resolveCollisionGroup(sameName) }
            .toMap()
        val fallbacks = segmentsByKey.keys
            .filter { it !in resolved }
            .associateWith { FALLBACK_NAME }
        return resolved + fallbacks
    }

    private fun <K> resolveCollisionGroup(entries: List<Pair<K, List<String>>>): List<Pair<K, String>> {
        if (entries.size == 1) {
            val (key, segments) = entries.single()
            return listOf(key to segments.last())
        }
        val maxDepth = entries.maxOf { it.second.size }
        return (1..maxDepth)
            .asSequence()
            .map { depth -> entries.map { (key, segments) -> key to suffix(segments, depth) } }
            .firstOrNull { candidates -> candidates.distinctBy { it.second }.size == candidates.size }
            ?: entries.map { (key, segments) -> key to suffix(segments, maxDepth) }
    }

    private fun suffix(segments: List<String>, depth: Int): String =
        segments.takeLast(depth).joinToString(SEPARATOR)
}
