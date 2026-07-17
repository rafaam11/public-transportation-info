package com.rafaam11.businfo.data.remote

interface GitHubReleaseDataSource {
    suspend fun latestRelease(): RemoteResult<GitHubRelease?>
}

data class GitHubRelease(
    val tagName: String,
    val htmlUrl: String,
    val assets: List<GitHubReleaseAsset>,
)

data class GitHubReleaseAsset(val name: String, val downloadUrl: String)
