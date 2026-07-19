package com.rafaam11.businfo.data.location

import com.rafaam11.businfo.domain.GeoPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentLocationDataSourceTest {
    private val daegu = GeoPoint(128.628, 35.880)

    @Test
    fun `enabled providers prefer network before gps`() {
        assertEquals(
            listOf("network", "gps"),
            enabledLocationProviders(networkEnabled = true, gpsEnabled = true),
        )
    }

    @Test
    fun `network provider is tried before gps fallback`() = runTest {
        val calls = mutableListOf<String>()

        val result = resolveCurrentDaeguLocation(
            providers = listOf("network", "gps"),
            timeoutMillis = 1_000,
        ) { provider ->
            calls += provider
            if (provider == "gps") daegu else null
        }

        assertEquals(listOf("network", "gps"), calls)
        assertEquals(daegu, result.getOrThrow())
    }

    @Test
    fun `timed out network provider falls back to gps`() = runTest {
        val calls = mutableListOf<String>()

        val result = resolveCurrentDaeguLocation(
            providers = listOf("network", "gps"),
            timeoutMillis = 1_000,
        ) { provider ->
            calls += provider
            if (provider == "network") {
                delay(2_000)
                null
            } else {
                daegu
            }
        }

        assertEquals(listOf("network", "gps"), calls)
        assertEquals(daegu, result.getOrThrow())
    }

    @Test
    fun `missing or out of range points return unavailable failure`() = runTest {
        val result = resolveCurrentDaeguLocation(
            providers = listOf("network", "gps"),
            timeoutMillis = 1_000,
        ) { provider ->
            if (provider == "network") GeoPoint(126.978, 37.566) else null
        }

        assertTrue(result.exceptionOrNull() is CurrentLocationUnavailableException)
    }
}
