package com.rafaam11.businfo.domain

import java.time.Duration
import java.time.Instant

object FreshnessPolicy {
    fun classify(observedAt: Instant?, now: Instant): DataFreshness {
        if (observedAt == null) return DataFreshness.UNAVAILABLE
        val measuredAge = Duration.between(observedAt, now)
        val age = if (measuredAge.isNegative) Duration.ZERO else measuredAge
        return when {
            age <= Duration.ofSeconds(15) -> DataFreshness.FRESH
            age <= Duration.ofSeconds(30) -> DataFreshness.DELAYED
            else -> DataFreshness.STALE
        }
    }
}
