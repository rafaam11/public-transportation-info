package com.rafaam11.businfo.domain

data class AppUpdateInfo(
    val latestVersion: AppVersion,
    val tagName: String,
    val downloadUrl: String,
    val releaseHtmlUrl: String,
    val assetName: String,
)

sealed interface UpdateCheckOutcome {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckOutcome
    data object UpToDate : UpdateCheckOutcome
    data class Failed(val error: BusDataError) : UpdateCheckOutcome
}
