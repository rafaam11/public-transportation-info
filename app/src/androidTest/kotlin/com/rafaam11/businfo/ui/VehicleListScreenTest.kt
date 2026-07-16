package com.rafaam11.businfo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.VehicleBatch
import com.rafaam11.businfo.domain.VehicleSnapshot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VehicleListScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun needsKeyScreenAcceptsInputAndSubmittingPreventsDuplicateSubmit() {
        var state by mutableStateOf<VehicleListUiState>(VehicleListUiState.NeedsKey())
        var submissions = 0
        compose.setContent {
            VehicleListScreen(
                state = state,
                onSubmitKey = {
                    submissions++
                    state = VehicleListUiState.NeedsKey(submitting = true)
                },
                onRefresh = {},
                onClearKey = {},
            )
        }

        compose.onNodeWithText("공공데이터 API 키 입력").assertIsDisplayed()
        compose.onNodeWithText("API 키").performTextInput("draft")
        compose.onNodeWithText("키 저장하고 조회").assertIsEnabled().performClick()
        compose.onNodeWithText("키 확인 중").assertIsNotEnabled()

        assertEquals(1, submissions)
    }

    @Test fun normalAndSuccessfulEmptyContentArePresented() {
        var state by mutableStateOf<VehicleListUiState>(VehicleListUiState.Content(batch(listOf(vehicle)), false))
        compose.setContent { screen(state) }
        compose.onNodeWithText("1대 운행").assertIsDisplayed()
        compose.onNodeWithText(vehicle.primaryText()).assertIsDisplayed()

        compose.runOnIdle { state = VehicleListUiState.Content(batch(emptyList()), false) }
        compose.onNodeWithText("0대 운행").assertIsDisplayed()
        compose.onNodeWithText("현재 운행 차량 없음").assertIsDisplayed()
    }

    @Test fun staleRetainedContentAndEachErrorArePresented() {
        val errors = listOf(
            BusDataError.InvalidCredential,
            BusDataError.NetworkUnavailable,
            BusDataError.ServiceUnavailable,
            BusDataError.MalformedResponse,
            BusDataError.RateLimited,
        )

        var state by mutableStateOf<VehicleListUiState>(
            VehicleListUiState.Content(batch(listOf(vehicle)), false, errors.first()),
        )
        compose.setContent { screen(state) }

        errors.forEachIndexed { index, error ->
            if (index > 0) {
                compose.runOnIdle {
                    state = VehicleListUiState.Content(batch(listOf(vehicle)), false, error)
                }
            }
            compose.onNodeWithText(error.userMessage()).assertIsDisplayed()
            compose.onNodeWithText("오래됨", substring = true).assertIsDisplayed()
            compose.onNodeWithText(vehicle.primaryText()).assertIsDisplayed()
        }
    }

    @Test fun keyChangeAndRefreshActionsInvokeCallbacks() {
        var refreshes = 0
        var keyChanges = 0
        compose.setContent {
            VehicleListScreen(
                state = VehicleListUiState.Content(batch(listOf(vehicle)), false),
                onSubmitKey = {},
                onRefresh = { refreshes++ },
                onClearKey = { keyChanges++ },
            )
        }

        compose.onNodeWithText("새로고침").performClick()
        compose.onNodeWithText("API 키 변경").performClick()

        assertEquals(1, refreshes)
        assertEquals(1, keyChanges)
    }

    @Composable
    private fun screen(state: VehicleListUiState) = VehicleListScreen(
        state = state,
        onSubmitKey = {},
        onRefresh = {},
        onClearKey = {},
    )

    private fun batch(vehicles: List<VehicleSnapshot>) = VehicleBatch.from(vehicles, Instant.EPOCH)

    private val vehicle = VehicleSnapshot(
        routeId = "3000814001",
        routeNo = "814",
        moveDirection = "0",
        stopId = "stop-a",
        stopSequence = 12,
        latitude = 35.8,
        longitude = 128.6,
        arrivalState = "soon",
        busTypeCode2 = "1",
        busTypeCode3 = "2",
    )
}
