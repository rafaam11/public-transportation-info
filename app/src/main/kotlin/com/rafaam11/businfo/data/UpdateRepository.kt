package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.remote.GitHubReleaseDataSource
import com.rafaam11.businfo.data.remote.RemoteResult
import com.rafaam11.businfo.domain.AppUpdateInfo
import com.rafaam11.businfo.domain.AppVersion
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.UpdateCheckOutcome

interface UpdateRepository {
    suspend fun checkForUpdate(): UpdateCheckOutcome
}

class GitHubUpdateRepository(
    private val remote: GitHubReleaseDataSource,
    private val currentVersionName: String,
) : UpdateRepository {
    override suspend fun checkForUpdate(): UpdateCheckOutcome {
        val current = AppVersion.parse(currentVersionName)
            ?: return UpdateCheckOutcome.Failed(BusDataError.MalformedResponse)
        return when (val result = remote.latestRelease()) {
            is RemoteResult.Failure -> UpdateCheckOutcome.Failed(result.error)
            is RemoteResult.Success -> {
                val release = result.value ?: return UpdateCheckOutcome.UpToDate
                val latest = AppVersion.parse(release.tagName)
                    ?: return UpdateCheckOutcome.Failed(BusDataError.MalformedResponse)
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return UpdateCheckOutcome.Failed(BusDataError.MalformedResponse)
                if (latest > current) {
                    UpdateCheckOutcome.UpdateAvailable(
                        AppUpdateInfo(
                            latestVersion = latest,
                            tagName = release.tagName,
                            downloadUrl = apkAsset.downloadUrl,
                            releaseHtmlUrl = release.htmlUrl,
                            assetName = apkAsset.name,
                        ),
                    )
                } else {
                    UpdateCheckOutcome.UpToDate
                }
            }
        }
    }
}
