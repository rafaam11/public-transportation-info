package com.rafaam11.businfo.widget

import android.content.Context
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.CommuteSlot

interface WidgetPreferenceGateway {
    fun slot(appWidgetId: Int): CommuteSlot?
    fun saveSlot(appWidgetId: Int, slot: CommuteSlot)
    fun errorState(appWidgetId: Int): WidgetRefreshError?
    fun saveError(appWidgetId: Int, error: BusDataError?, atEpochMillis: Long?)
    fun clear(appWidgetId: Int)
}

data class WidgetRefreshError(val error: BusDataError, val atEpochMillis: Long)

class WidgetPreferenceStore(context: Context) : WidgetPreferenceGateway {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun slot(appWidgetId: Int): CommuteSlot? {
        val key = slotKey(appWidgetId)
        if (!preferences.contains(key)) return null
        return runCatching {
            CommuteSlot.valueOf(requireNotNull(preferences.getString(key, null)))
        }.getOrElse {
            preferences.edit().remove(key).apply()
            null
        }
    }

    override fun saveSlot(appWidgetId: Int, slot: CommuteSlot) {
        preferences.edit().putString(slotKey(appWidgetId), slot.name).apply()
    }

    override fun errorState(appWidgetId: Int): WidgetRefreshError? {
        val errorKey = errorKey(appWidgetId)
        val errorAtKey = errorAtKey(appWidgetId)
        val hasError = preferences.contains(errorKey)
        val hasErrorAt = preferences.contains(errorAtKey)
        val storedError = runCatching { preferences.getString(errorKey, null) }.getOrNull()
        val storedAt = runCatching {
            preferences.takeIf { hasErrorAt }?.getLong(errorAtKey, 0L)
        }.getOrNull()
        val error = storedError?.let(WidgetErrorToken::parse)
        if (error == null || storedAt == null) {
            if (hasError || hasErrorAt) clearError(appWidgetId)
            return null
        }
        return WidgetRefreshError(error, storedAt)
    }

    override fun saveError(appWidgetId: Int, error: BusDataError?, atEpochMillis: Long?) {
        if (error == null || atEpochMillis == null) {
            clearError(appWidgetId)
            return
        }
        preferences.edit()
            .putString(errorKey(appWidgetId), WidgetErrorToken.serialize(error))
            .putLong(errorAtKey(appWidgetId), atEpochMillis)
            .apply()
    }

    override fun clear(appWidgetId: Int) {
        preferences.edit()
            .remove(slotKey(appWidgetId))
            .remove(errorKey(appWidgetId))
            .remove(errorAtKey(appWidgetId))
            .apply()
    }

    private fun clearError(appWidgetId: Int) {
        preferences.edit()
            .remove(errorKey(appWidgetId))
            .remove(errorAtKey(appWidgetId))
            .apply()
    }

    private fun slotKey(appWidgetId: Int) = "slot:$appWidgetId"
    private fun errorKey(appWidgetId: Int) = "error:$appWidgetId"
    private fun errorAtKey(appWidgetId: Int) = "errorAt:$appWidgetId"

    private companion object {
        const val PREFERENCES_NAME = "commute-widget"
    }
}

internal object WidgetErrorToken {
    fun serialize(error: BusDataError): String = when (error) {
        BusDataError.InvalidCredential -> "invalid_credential"
        BusDataError.RateLimited -> "rate_limited"
        BusDataError.NetworkUnavailable -> "network_unavailable"
        BusDataError.ServiceUnavailable -> "service_unavailable"
        BusDataError.MalformedResponse -> "malformed_response"
    }

    fun parse(value: String): BusDataError? = when (value) {
        "invalid_credential", "InvalidCredential" -> BusDataError.InvalidCredential
        "rate_limited", "RateLimited" -> BusDataError.RateLimited
        "network_unavailable", "NetworkUnavailable" -> BusDataError.NetworkUnavailable
        "service_unavailable", "ServiceUnavailable" -> BusDataError.ServiceUnavailable
        "malformed_response", "MalformedResponse" -> BusDataError.MalformedResponse
        else -> null
    }
}
