package com.rafaam11.businfo.domain

data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

        fun parse(raw: String): AppVersion? {
            val match = PATTERN.matchEntire(raw.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            return AppVersion(major.toInt(), minor.toInt(), patch.toInt())
        }
    }
}
