package com.rafaam11.businfo.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import com.rafaam11.businfo.data.DownloadOutcome
import com.rafaam11.businfo.data.UpdateDownloader
import com.rafaam11.businfo.domain.AppUpdateInfo
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidUpdateDownloader(private val context: Context) : UpdateDownloader {
    override suspend fun download(info: AppUpdateInfo): DownloadOutcome =
        suspendCancellableCoroutine { continuation ->
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
                .setTitle("대구 버스 업데이트 ${info.tagName}")
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, info.assetName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setMimeType("application/vnd.android.package-archive")
            val downloadId = manager.enqueue(request)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                    runCatching { context.unregisterReceiver(this) }
                    if (!continuation.isActive) return
                    manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                        if (!cursor.moveToFirst()) {
                            continuation.resume(DownloadOutcome.Failed("다운로드 결과를 확인할 수 없습니다"))
                            return@use
                        }
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), info.assetName)
                            continuation.resume(DownloadOutcome.Success(file))
                        } else {
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            continuation.resume(DownloadOutcome.Failed("다운로드 실패 (코드 $reason)"))
                        }
                    }
                }
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            continuation.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
                manager.remove(downloadId)
            }
        }
}
