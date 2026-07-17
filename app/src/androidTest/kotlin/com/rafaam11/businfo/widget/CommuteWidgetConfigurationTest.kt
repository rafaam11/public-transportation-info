package com.rafaam11.businfo.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommuteWidgetConfigurationTest {
    @Test fun invalidWidgetIdIsRejectedBeforeUi() {
        launchAndAssertCanceled(AppWidgetManager.INVALID_APPWIDGET_ID)
    }

    @Test fun unownedWidgetIdIsRejectedBeforeUiAndSave() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val unownedId = 42
        WidgetPreferenceStore(context).clear(unownedId)

        launchAndAssertCanceled(unownedId)

        assertEquals(null, WidgetPreferenceStore(context).slot(unownedId))
        assertEquals(
            null,
            AppWidgetManager.getInstance(context).getAppWidgetInfo(unownedId)?.provider,
        )
    }

    private fun launchAndAssertCanceled(appWidgetId: Int) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, CommuteWidgetConfigurationActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

        val scenario = ActivityScenario.launchActivityForResult<CommuteWidgetConfigurationActivity>(intent)

        val result = scenario.result
        assertEquals(Activity.RESULT_CANCELED, result.resultCode)
    }
}
