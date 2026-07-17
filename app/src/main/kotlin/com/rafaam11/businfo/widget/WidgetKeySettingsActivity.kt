package com.rafaam11.businfo.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.rafaam11.businfo.MainActivity

class WidgetKeySettingsActivity : ComponentActivity() {
    private val keySettingsRequests by lazy { KeySettingsRequestStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keySettingsRequests.request()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
