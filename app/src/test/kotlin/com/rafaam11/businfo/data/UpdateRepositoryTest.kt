package com.rafaam11.businfo.data

import com.rafaam11.businfo.data.remote.GitHubRelease
import com.rafaam11.businfo.data.remote.GitHubReleaseAsset
import com.rafaam11.businfo.data.remote.GitHubReleaseDataSource
import com.rafaam11.businfo.data.remote.RemoteResult
import com.rafaam11.businfo.domain.BusDataError
import com.rafaam11.businfo.domain.UpdateCheckOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryTest {
    @Test fun `newer published release reports an available update`() = runTest {
        val remote = FakeGitHubReleaseDataSource(
            RemoteResult.Success(
                GitHubRelease("v0.6.0", "https://example/releases/v0.6.0", listOf(GitHubReleaseAsset("app.apk", "https://example/app.apk"))),
            ),
        )
        val repository = GitHubUpdateRepository(remote, "0.5.0")

        val outcome = repository.checkForUpdate()

        assertTrue(outcome is UpdateCheckOutcome.UpdateAvailable)
        val info = (outcome as UpdateCheckOutcome.UpdateAvailable).info
        assertEquals("v0.6.0", info.tagName)
        assertEquals("https://example/app.apk", info.downloadUrl)
        assertEquals("app.apk", info.assetName)
    }

    @Test fun `same or older release reports up to date`() = runTest {
        val remote = FakeGitHubReleaseDataSource(
            RemoteResult.Success(
                GitHubRelease("v0.5.0", "https://example/releases/v0.5.0", listOf(GitHubReleaseAsset("app.apk", "https://example/app.apk"))),
            ),
        )
        val repository = GitHubUpdateRepository(remote, "0.5.0")

        assertEquals(UpdateCheckOutcome.UpToDate, repository.checkForUpdate())
    }

    @Test fun `no published release yet reports up to date`() = runTest {
        val remote = FakeGitHubReleaseDataSource(RemoteResult.Success(null))
        val repository = GitHubUpdateRepository(remote, "0.5.0")

        assertEquals(UpdateCheckOutcome.UpToDate, repository.checkForUpdate())
    }

    @Test fun `release without an apk asset is treated as malformed`() = runTest {
        val remote = FakeGitHubReleaseDataSource(
            RemoteResult.Success(GitHubRelease("v0.6.0", "https://example/releases/v0.6.0", emptyList())),
        )
        val repository = GitHubUpdateRepository(remote, "0.5.0")

        assertEquals(UpdateCheckOutcome.Failed(BusDataError.MalformedResponse), repository.checkForUpdate())
    }

    @Test fun `remote failure propagates as failed outcome`() = runTest {
        val remote = FakeGitHubReleaseDataSource(RemoteResult.Failure(BusDataError.NetworkUnavailable))
        val repository = GitHubUpdateRepository(remote, "0.5.0")

        assertEquals(UpdateCheckOutcome.Failed(BusDataError.NetworkUnavailable), repository.checkForUpdate())
    }

    private class FakeGitHubReleaseDataSource(
        private val result: RemoteResult<GitHubRelease?>,
    ) : GitHubReleaseDataSource {
        override suspend fun latestRelease(): RemoteResult<GitHubRelease?> = result
    }
}
