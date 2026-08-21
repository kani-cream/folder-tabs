package com.github.kanicream.foldertabs.grouping

/**
 * Natural, case-insensitive string order (design section 7): digit runs compare by numeric
 * value so `file2` < `file9` < `file10`; everything else compares by lower-cased characters.
 *
 * Returns 0 for strings that differ only by case; callers add a full-path tie-breaker.
 */
object NaturalOrderComparator : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val endA = digitRunEnd(a, i)
                val endB = digitRunEnd(b, j)
                val cmp = compareDigitRuns(a.substring(i, endA), b.substring(j, endB))
                if (cmp != 0) return cmp
                i = endA
                j = endB
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i).compareTo(b.length - j)
    }

    private fun digitRunEnd(s: String, start: Int): Int {
        var end = start
        while (end < s.length && s[end].isDigit()) end++
        return end
    }

    private fun compareDigitRuns(x: String, y: String): Int {
        val tx = x.trimStart('0')
        val ty = y.trimStart('0')
        if (tx.length != ty.length) return tx.length.compareTo(ty.length)
        return tx.compareTo(ty)
    }
}
