package com.rafaam11.businfo.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PollingPolicyTest {
    @Test fun successUsesEightSeconds() = assertEquals(PollDecision.Wait(8), PollingPolicy.after(PollResult.Success))
    @Test fun firstFailureUsesFifteenSeconds() = assertEquals(PollDecision.Wait(15), PollingPolicy.after(PollResult.TransientFailure(1)))
    @Test fun laterFailureUsesThirtySeconds() = assertEquals(PollDecision.Wait(30), PollingPolicy.after(PollResult.TransientFailure(2)))
    @Test fun authenticationStops() = assertEquals(PollDecision.Stop, PollingPolicy.after(PollResult.AuthenticationFailure))
    @Test fun quotaStops() = assertEquals(PollDecision.Stop, PollingPolicy.after(PollResult.QuotaExceeded))
}
