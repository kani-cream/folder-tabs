package com.github.kanicream.foldertabs.grouping

/**
 * Turns a directory path into a [DirectoryLabelSource] (design section 6.3): paths under the
 * project base directory become project-relative segments (empty for the root itself),
 * everything else keeps its full path. Pure string logic; VFS paths always use `/`.
 */
object DirectoryPathSegments {

    fun of(directoryPath: String, projectBasePath: String?): DirectoryLabelSource {
        val relative = relativeToBase(directoryPath, projectBasePath)
        return if (relative != null) {
            DirectoryLabelSource(split(relative), projectRelative = true)
        } else {
            DirectoryLabelSource(split(directoryPath), projectRelative = false)
        }
    }

    private fun split(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }

    private fun relativeToBase(path: String, base: String?): String? {
        if (base.isNullOrEmpty()) return null
        val normalizedBase = base.trimEnd('/')
        return when {
            path == normalizedBase -> ""
            path.startsWith("$normalizedBase/") -> path.removePrefix("$normalizedBase/")
            else -> null
        }
    }
}
