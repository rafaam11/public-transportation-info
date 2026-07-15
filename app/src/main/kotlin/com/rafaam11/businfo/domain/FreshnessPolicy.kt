package com.rafaam11.businfo.domain

import java.time.Duration
import java.time.Instant

object FreshnessPolicy {
    fun classify(observedAt: Instant?, now: Instant): DataFreshness {
        if (observedAt == null) return DataFreshness.UNAVAILABLE
        val ageSeconds = Duration.between(observedAt, now).seconds.coerceAtLeast(0)
        return when {
            ageSeconds <= 15 -> DataFreshness.FRESH
            ageSeconds <= 30 -> DataFreshness.DELAYED
            else -> DataFreshness.STALE
        }
    }
}
