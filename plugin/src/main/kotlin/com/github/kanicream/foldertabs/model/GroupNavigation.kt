package com.github.kanicream.foldertabs.model

/** Direction of a keyboard move between directory groups (design section 8.4, issue #24). */
enum class GroupDirection(internal val step: Int) {
    NEXT(1),
    PREVIOUS(-1),
}

/**
 * Pure neighbour lookup for the Next / Previous Folder Group actions (design section 8.4).
 *
 * The list is whatever the invoking header currently shows, in its displayed order, so a split
 * pane only ever cycles through its own groups. Navigation wraps at both ends, like the
 * platform's Next Tab / Previous Tab. With fewer than two items, or when the current item is
 * not in the list (the header has no active group), there is nowhere to go.
 */
object GroupNavigation {

    fun <T> adjacent(items: List<T>, current: T?, direction: GroupDirection): T? {
        if (items.size < 2) return null
        val index = items.indexOf(current)
        if (index < 0) return null
        return items[Math.floorMod(index + direction.step, items.size)]
    }
}
