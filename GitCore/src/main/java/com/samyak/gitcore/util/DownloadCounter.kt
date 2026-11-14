package com.samyak.gitcore.util

import java.util.Locale
import kotlin.math.round

/**
 * Utility for computing and formatting the total number of downloads for an app.
 *
 * GitHub reports a download count per release asset. The "total app downloads"
 * is the sum of those per-asset counts. This helper is model-agnostic so it stays
 * reusable across the GitCore module without depending on app data models.
 */
object DownloadCounter {

    /**
     * Sum a collection of per-asset download counts into a single non-negative total.
     * Uses [Long] to avoid overflow when a popular repo has millions of downloads.
     */
    fun total(downloadCounts: Iterable<Int>): Long {
        return downloadCounts.sumOf { it.coerceAtLeast(0).toLong() }
    }

    /**
     * Sum a collection of 64-bit download counts into a single non-negative total.
     */
    fun totalFromLongs(downloadCounts: Iterable<Long>): Long {
        return downloadCounts.sumOf { it.coerceAtLeast(0L) }
    }

    /**
     * Format a download total into a compact, human-readable string.
     * Negative values are clamped to `"0"`. Values near metric boundaries roll over cleanly
     * (e.g., `999_950` -> `"1.0M"` rather than `"1000.0K"`).
     *
     * Examples: 0 -> "0", 999 -> "999", 1_500 -> "1.5K", 2_300_000 -> "2.3M".
     */
    fun format(count: Long): String {
        val safeCount = count.coerceAtLeast(0L)
        if (safeCount < 1_000L) {
            return safeCount.toString()
        }

        val units = arrayOf(
            1_000_000_000.0 to "B",
            1_000_000.0 to "M",
            1_000.0 to "K"
        )

        for (index in units.indices) {
            val (divisor, suffix) = units[index]
            if (safeCount >= divisor.toLong()) {
                val roundedTenths = round((safeCount / divisor) * 10.0) / 10.0
                if (roundedTenths >= 1000.0 && index > 0) {
                    val (nextDivisor, nextSuffix) = units[index - 1]
                    val promoted = round((safeCount / nextDivisor) * 10.0) / 10.0
                    return String.format(Locale.US, "%.1f%s", promoted, nextSuffix)
                }
                return String.format(Locale.US, "%.1f%s", roundedTenths, suffix)
            }
        }
        return safeCount.toString()
    }

    fun format(count: Int): String = format(count.toLong())

    /**
     * Convenience: sum and format in one step.
     */
    fun totalFormatted(downloadCounts: Iterable<Int>): String {
        return format(total(downloadCounts))
    }

    fun totalFormattedFromLongs(downloadCounts: Iterable<Long>): String {
        return format(totalFromLongs(downloadCounts))
    }
}
