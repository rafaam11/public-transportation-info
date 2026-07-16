package com.rafaam11.businfo.ui

import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.VehicleBatch

sealed interface VehicleListUiState {
    data object Starting : VehicleListUiState

    data class NeedsKey(
        val submitting: Boolean = false,
        val error: BusDataError? = null,
    ) : VehicleListUiState

    data class Content(
        val batch: VehicleBatch?,
        val refreshing: Boolean,
        val error: BusDataError? = null,
    ) : VehicleListUiState
}
