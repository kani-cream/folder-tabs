package com.github.kanicream.foldertabs.grouping

/**
 * Where a directory label comes from: root-first path segments, relative to the project
 * base directory when [projectRelative] is true (empty = the project root itself),
 * otherwise the full path.
 */
data class DirectoryLabelSource(
    val segments: List<String>,
    val projectRelative: Boolean,
)

/**
 * How group labels are rendered (design section 6.5): [minDepth] segments at least
 * ([PROJECT_ROOT_DEPTH] = always up to the project root), prefixed with `~/<projectName>/`
 * once a project-relative label reaches the root.
 */
data class GroupLabelPolicy(
    val minDepth: Int,
    val projectName: String,
) {
    init {
        require(minDepth == PROJECT_ROOT_DEPTH || minDepth >= 1) { "minDepth must be >= 1 or PROJECT_ROOT_DEPTH" }
    }

    val effectiveMinDepth: Int get() = if (minDepth == PROJECT_ROOT_DEPTH) Int.MAX_VALUE else minDepth

    companion object {
        /** Sentinel for "show the whole path from the project root". */
        const val PROJECT_ROOT_DEPTH: Int = 0
        const val DEFAULT_DEPTH: Int = 2
    }
}

/**
 * Resolves display names for directories (design sections 6 and 6.5): start from the
 * configured depth and, for directories that still share a label, add parent segments until
 * the labels differ. Pure; no VFS dependency.
 */
object MinimalUniquePathResolver {

    /** Label for entries whose path could not be resolved (design section 14). */
    const val FALLBACK_NAME: String = "Other"

    /** UI separator is always `/`, regardless of OS (design section 6.4). */
    const val SEPARATOR: String = "/"

    /** Marks a label that starts at the project root (design section 6.5). */
    const val PROJECT_ROOT_PREFIX: String = "~"

    fun <K> resolve(sources: Map<K, DirectoryLabelSource>, policy: GroupLabelPolicy): Map<K, String> {
        val resolved = sources
            .filterValues { it.segments.isNotEmpty() || it.projectRelative }
            .entries
            .groupBy({ it.value.segments.lastOrNull() ?: "" }, { it.key to it.value })
            .flatMap { (_, sameName) -> resolveCollisionGroup(sameName, policy) }
            .toMap()
        val fallbacks = sources.keys.filter { it !in resolved }.associateWith { FALLBACK_NAME }
        return resolved + fallbacks
    }

    private fun <K> resolveCollisionGroup(
        entries: List<Pair<K, DirectoryLabelSource>>,
        policy: GroupLabelPolicy,
    ): List<Pair<K, String>> {
        val maxDepth = entries.maxOf { it.second.segments.size }.coerceAtLeast(1)
        val start = policy.effectiveMinDepth.coerceAtMost(maxDepth)
        if (entries.size == 1) {
            val (key, source) = entries.single()
            return listOf(key to label(source, start, policy))
        }
        return (start..maxDepth)
            .asSequence()
            .map { depth -> entries.map { (key, source) -> key to label(source, depth, policy) } }
            .firstOrNull { candidates -> candidates.distinctBy { it.second }.size == candidates.size }
            ?: entries.map { (key, source) -> key to label(source, maxDepth, policy) }
    }

    /** Design section 6.5 pseudo-code. */
    fun label(source: DirectoryLabelSource, depth: Int, policy: GroupLabelPolicy): String {
        val segments = source.segments
        if (source.projectRelative && depth >= segments.size) {
            return (listOf(PROJECT_ROOT_PREFIX, policy.projectName) + segments).joinToString(SEPARATOR)
        }
        return segments.takeLast(depth).joinToString(SEPARATOR)
    }
}
