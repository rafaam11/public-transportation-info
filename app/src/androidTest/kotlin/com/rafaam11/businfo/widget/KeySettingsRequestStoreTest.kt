package com.rafaam11.businfo.widget

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeySettingsRequestStoreTest {
    @Test fun trustedRequestIsConsumedExactlyOnce() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = KeySettingsRequestStore(context)
        store.consume()

        store.request()

        assertTrue(store.consume())
        assertFalse(store.consume())
    }
}
