package dev.sajancodes.hardblock.domain.update

data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int, val prerelease: String? = null) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }).let { if (it != 0) return it }
        if (prerelease == other.prerelease) return 0
        if (prerelease == null) return 1
        if (other.prerelease == null) return -1
        val a = prerelease.split('.'); val b = other.prerelease.split('.')
        repeat(minOf(a.size, b.size)) { i ->
            val x = a[i].all(Char::isDigit); val y = b[i].all(Char::isDigit)
            val order = when { x && y -> compareValuesBy(a[i], b[i], { it.length }, { it }); x -> -1; y -> 1; else -> a[i].compareTo(b[i]) }
            if (order != 0) return order
        }
        return a.size.compareTo(b.size)
    }
    companion object {
        fun parse(tag: String): ReleaseVersion? {
            val m = Regex("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$").matchEntire(tag) ?: return null
            val pre = m.groupValues[4].ifEmpty { null }
            if ('+' in tag && tag.substringAfter('+').split('.').any { it.isEmpty() }) return null
            if (pre?.split('.')?.any { it.isEmpty() || (it.all(Char::isDigit) && it.length > 1 && it.startsWith('0')) } == true) return null
            return ReleaseVersion(m.groupValues[1].toIntOrNull() ?: return null, m.groupValues[2].toIntOrNull() ?: return null,
                m.groupValues[3].toIntOrNull() ?: return null, pre)
        }
    }
}
