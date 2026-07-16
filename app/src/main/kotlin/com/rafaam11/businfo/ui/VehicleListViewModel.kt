package com.rafaam11.businfo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rafaam11.businfo.data.BusRepository
import com.rafaam11.businfo.domain.VehicleBatch
import com.rafaam11.businfo.domain.VehicleLoadResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VehicleListViewModel(
    private val repository: BusRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _uiState = MutableStateFlow<VehicleListUiState>(VehicleListUiState.Starting)
    val uiState: StateFlow<VehicleListUiState> = _uiState.asStateFlow()

    private var eventJob: Job? = null

    init {
        if (repository.savedKeyExists()) {
            startRefresh(batch = null)
        } else {
            _uiState.value = VehicleListUiState.NeedsKey()
        }
    }

    fun submitKey(key: String) {
        val current = _uiState.value as? VehicleListUiState.NeedsKey ?: return
        if (current.submitting) return

        _uiState.value = VehicleListUiState.NeedsKey(submitting = true)
        eventJob = viewModelScope.launch(dispatcher) {
            val error = repository.validateAndSave(key)
            if (error != null) {
                _uiState.value = VehicleListUiState.NeedsKey(error = error)
            } else {
                _uiState.value = VehicleListUiState.Content(batch = null, refreshing = true)
                loadVehicles()
            }
        }
    }

    fun refresh() {
        val current = _uiState.value as? VehicleListUiState.Content ?: return
        if (current.refreshing) return

        startRefresh(current.batch)
    }

    fun clearKey() {
        eventJob?.cancel()
        eventJob = null
        repository.clearKey()
        _uiState.value = VehicleListUiState.NeedsKey()
    }

    private fun startRefresh(batch: VehicleBatch?) {
        _uiState.value = VehicleListUiState.Content(batch = batch, refreshing = true)
        eventJob = viewModelScope.launch(dispatcher) {
            loadVehicles()
        }
    }

    private suspend fun loadVehicles() {
        _uiState.value = when (val result = repository.refreshVehicles()) {
            is VehicleLoadResult.Success -> VehicleListUiState.Content(
                batch = result.batch,
                refreshing = false,
            )
            is VehicleLoadResult.Failure -> VehicleListUiState.Content(
                batch = result.retained,
                refreshing = false,
                error = result.error,
            )
        }
    }
}
