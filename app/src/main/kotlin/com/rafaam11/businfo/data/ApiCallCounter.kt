package com.rafaam11.businfo.data

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ApiCallCounter {
    suspend fun increment(at: Instant)
    suspend fun count(at: Instant): Int
}

class MemoryApiCallCounter : ApiCallCounter {
    private val counts = mutableMapOf<String, Int>()
    private val mutex = Mutex()

    override suspend fun increment(at: Instant) = mutex.withLock {
        val key = at.dayKey()
        counts[key] = counts.getOrDefault(key, 0) + 1
    }

    override suspend fun count(at: Instant): Int = mutex.withLock { counts.getOrDefault(at.dayKey(), 0) }
}

class SharedPreferencesApiCallCounter(context: Context) : ApiCallCounter {
    private val preferences = context.getSharedPreferences("api-call-diagnostics", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun increment(at: Instant) = mutex.withLock {
        val key = at.dayKey()
        preferences.edit().putInt(key, preferences.getInt(key, 0) + 1).apply()
    }

    override suspend fun count(at: Instant): Int = mutex.withLock { preferences.getInt(at.dayKey(), 0) }
}

private fun Instant.dayKey(): String = "calls-${atZone(SEOUL_ZONE).toLocalDate()}"

private val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
