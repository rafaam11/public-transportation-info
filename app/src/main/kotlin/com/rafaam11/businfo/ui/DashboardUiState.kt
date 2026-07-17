package com.rafaam11.businfo.ui

import com.rafaam11.businfo.data.DirectionOption
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.CommuteSlot
import com.rafaam11.businfo.domain.FavoriteDashboardSnapshot
import com.rafaam11.businfo.domain.FavoriteSelection
import com.rafaam11.businfo.domain.RouteStop
import com.rafaam11.businfo.domain.RouteSummary
import com.rafaam11.businfo.domain.VehicleBatch

sealed interface AppUiState {
    data object Starting : AppUiState
    data class NeedsKey(val submitting: Boolean = false, val error: BusDataError? = null) : AppUiState
    data class Ready(
        val cards: List<DashboardCardUiState>,
        val catalogPreparing: Boolean = false,
        val catalogError: BusDataError? = null,
    ) : AppUiState
}

sealed interface DashboardCardUiState {
    val slot: CommuteSlot
    data class Empty(override val slot: CommuteSlot) : DashboardCardUiState
    data class Configured(
        override val slot: CommuteSlot,
        val snapshot: FavoriteDashboardSnapshot,
        val refreshing: Boolean,
        val errorMessage: String?,
    ) : DashboardCardUiState
}

data class SetupUiState(
    val slot: CommuteSlot = CommuteSlot.MORNING,
    val existing: FavoriteSelection? = null,
    val query: String = "",
    val routes: List<RouteSummary> = emptyList(),
    val selectedRoute: RouteSummary? = null,
    val directions: List<DirectionOption> = emptyList(),
    val selectedDirection: DirectionOption? = null,
    val loading: Boolean = false,
    val error: BusDataError? = null,
)

data class NamedVehicle(
    val directionLabel: String,
    val stopName: String,
    val sequence: Int?,
    val arrivalState: String?,
)

data class DetailUiState(
    val selection: FavoriteSelection? = null,
    val batch: VehicleBatch? = null,
    val vehicles: List<NamedVehicle> = emptyList(),
    val refreshing: Boolean = false,
    val error: BusDataError? = null,
)

fun dashboardCards(
    snapshots: List<FavoriteDashboardSnapshot>,
    errors: Map<CommuteSlot, String>,
    refreshing: Set<CommuteSlot>,
): List<DashboardCardUiState> {
    val bySlot = snapshots.associateBy { it.selection.slot }
    return CommuteSlot.entries.map { slot ->
        bySlot[slot]?.let {
            DashboardCardUiState.Configured(slot, it, slot in refreshing, errors[slot])
        } ?: DashboardCardUiState.Empty(slot)
    }
}
