package com.rafaam11.businfo.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rafaam11.businfo.domain.CommuteSlot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommuteWidgetConfigurationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun morningChoiceReturnsWidgetIdAndPersistsSlot() = chooseAndVerify("출근", CommuteSlot.MORNING, 41)

    @Test fun eveningChoiceReturnsWidgetIdAndPersistsSlot() = chooseAndVerify("퇴근", CommuteSlot.EVENING, 42)

    private fun chooseAndVerify(label: String, slot: CommuteSlot, appWidgetId: Int) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WidgetPreferenceStore(context).clear(appWidgetId)
        val intent = Intent(context, CommuteWidgetConfigurationActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

        val scenario = ActivityScenario.launchActivityForResult<CommuteWidgetConfigurationActivity>(intent)
        compose.onNodeWithText(label).performClick()

        val result = scenario.result
        assertEquals(Activity.RESULT_OK, result.resultCode)
        assertEquals(appWidgetId, result.resultData?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertEquals(slot, WidgetPreferenceStore(context).slot(appWidgetId))
    }
}
