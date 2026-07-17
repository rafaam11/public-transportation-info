package com.rafaam11.businfo.widget

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sun.misc.Unsafe

class WidgetPreferenceStoreTest {
    @Test fun `wrong typed persisted slot returns null and removes only slot key`() {
        val preferences = sharedPreferencesWith(
            "error:$WIDGET_ID" to "ServiceUnavailable",
        )
        preferences.edit().putInt("slot:$WIDGET_ID", 7).apply()
        val store = widgetPreferenceStoreWith(preferences)

        assertNull(store.slot(WIDGET_ID))
        assertFalse(preferences.contains("slot:$WIDGET_ID"))
        assertTrue(preferences.contains("error:$WIDGET_ID"))
    }

    private fun sharedPreferencesWith(vararg entries: Pair<String, Any>): SharedPreferences {
        val values = mutableMapOf(*entries)
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "putInt" -> proxy.also { values[args!![0] as String] = args[1] as Int }
                "remove" -> proxy.also { values.remove(args!![0] as String) }
                "apply" -> null
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getString" -> values[args!![0] as String] as String?
                "contains" -> values.containsKey(args!![0] as String)
                "edit" -> editor
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
    }

    private fun widgetPreferenceStoreWith(preferences: SharedPreferences): WidgetPreferenceStore {
        val unsafeField = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null) as Unsafe
        return (unsafe.allocateInstance(WidgetPreferenceStore::class.java) as WidgetPreferenceStore).also { store ->
            WidgetPreferenceStore::class.java.getDeclaredField("preferences").apply {
                isAccessible = true
                set(store, preferences)
            }
        }
    }

    private companion object {
        const val WIDGET_ID = 42
    }
}
