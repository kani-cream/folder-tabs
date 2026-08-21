package com.github.kanicream.foldertabs.grouping

/**
 * Turns a directory path into root-first segments for [MinimalUniquePathResolver]
 * (design section 6.3): paths under the project base directory are taken relative to it,
 * everything else uses the full path. Pure string logic; VFS paths always use `/`.
 */
object DirectoryPathSegments {

    fun of(directoryPath: String, projectBasePath: String?): List<String> {
        val relative = relativeToBase(directoryPath, projectBasePath)
        val source = relative ?: directoryPath
        val segments = source.split('/').filter { it.isNotEmpty() }
        // The project base directory itself: keep its own name so it has a label.
        if (segments.isEmpty()) return listOf(lastSegment(directoryPath)).filter { it.isNotEmpty() }
        return segments
    }

    private fun relativeToBase(path: String, base: String?): String? {
        if (base.isNullOrEmpty()) return null
        val normalizedBase = base.trimEnd('/')
        return when {
            path == normalizedBase -> ""
            path.startsWith("$normalizedBase/") -> path.removePrefix("$normalizedBase/")
            else -> null
        }
    }

    private fun lastSegment(path: String): String = path.trimEnd('/').substringAfterLast('/')
}
