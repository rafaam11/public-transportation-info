package com.rafaam11.businfo.ui

import com.rafaam11.businfo.domain.AppUpdateInfo
import com.rafaam11.businfo.domain.BusDataError
import java.io.File

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Failed(val error: BusDataError) : UpdateUiState
    data class Available(val info: AppUpdateInfo, val download: DownloadUiState = DownloadUiState.NotStarted) : UpdateUiState
}

sealed interface DownloadUiState {
    data object NotStarted : DownloadUiState
    data object Downloading : DownloadUiState
    data class Downloaded(val file: File) : DownloadUiState
    data class Failed(val message: String) : DownloadUiState
}
