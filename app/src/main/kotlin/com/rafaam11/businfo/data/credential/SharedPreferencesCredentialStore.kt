package com.rafaam11.businfo.data.credential

import android.content.Context

class SharedPreferencesCredentialStore(context: Context) : CredentialStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(SERVICE_KEY, null)
        ?.trim()
        ?.takeIf(String::isNotBlank)

    override fun write(serviceKey: String) {
        preferences.edit().putString(SERVICE_KEY, serviceKey.trim()).apply()
    }

    override fun clear() {
        preferences.edit().remove(SERVICE_KEY).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "credentials"
        const val SERVICE_KEY = "daegu_service_key"
    }
}
