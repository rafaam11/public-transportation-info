package com.rafaam11.businfo.data

import com.rafaam11.businfo.domain.AppUpdateInfo
import java.io.File

interface UpdateDownloader {
    suspend fun download(info: AppUpdateInfo): DownloadOutcome
}

sealed interface DownloadOutcome {
    data class Success(val file: File) : DownloadOutcome
    data class Failed(val message: String) : DownloadOutcome
}
