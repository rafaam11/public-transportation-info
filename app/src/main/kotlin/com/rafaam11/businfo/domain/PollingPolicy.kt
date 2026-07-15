package com.rafaam11.businfo.domain

sealed interface PollResult {
    data object Success : PollResult
    data class TransientFailure(val consecutiveCount: Int) : PollResult
    data object AuthenticationFailure : PollResult
    data object QuotaExceeded : PollResult
}

sealed interface PollDecision {
    data class Wait(val seconds: Long) : PollDecision
    data object Stop : PollDecision
}

object PollingPolicy {
    fun after(result: PollResult): PollDecision = when (result) {
        PollResult.Success -> PollDecision.Wait(8)
        is PollResult.TransientFailure -> PollDecision.Wait(if (result.consecutiveCount == 1) 15 else 30)
        PollResult.AuthenticationFailure, PollResult.QuotaExceeded -> PollDecision.Stop
    }
}
