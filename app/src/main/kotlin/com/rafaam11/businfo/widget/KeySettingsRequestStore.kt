package com.rafaam11.businfo.widget

import android.content.Context

interface KeySettingsRequestGateway {
    fun request()
    fun consume(): Boolean
}

class KeySettingsRequestStore(context: Context) : KeySettingsRequestGateway {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun request() {
        preferences.edit().putBoolean(PENDING, true).apply()
    }

    override fun consume(): Boolean {
        if (!preferences.getBoolean(PENDING, false)) return false
        preferences.edit().remove(PENDING).apply()
        return true
    }

    private companion object {
        const val PREFERENCES_NAME = "key-settings-request"
        const val PENDING = "pending"
    }
}
