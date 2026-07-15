package com.rafaam11.businfo.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FreshnessPolicyTest {
    private val now = Instant.parse("2026-07-15T10:00:00Z")

    @Test fun fifteenSecondsIsFresh() = assertEquals(
        DataFreshness.FRESH,
        FreshnessPolicy.classify(now.minusSeconds(15), now),
    )

    @Test fun sixteenSecondsIsDelayed() = assertEquals(
        DataFreshness.DELAYED,
        FreshnessPolicy.classify(now.minusSeconds(16), now),
    )

    @Test fun thirtySecondsIsDelayed() = assertEquals(
        DataFreshness.DELAYED,
        FreshnessPolicy.classify(now.minusSeconds(30), now),
    )

    @Test fun thirtyOneSecondsIsStale() = assertEquals(
        DataFreshness.STALE,
        FreshnessPolicy.classify(now.minusSeconds(31), now),
    )

    @Test fun futureClockSkewIsFresh() = assertEquals(
        DataFreshness.FRESH,
        FreshnessPolicy.classify(now.plusSeconds(2), now),
    )
}
