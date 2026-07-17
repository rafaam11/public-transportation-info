package com.rafaam11.businfo.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class UpdateInstaller(private val context: Context) {
    fun canRequestInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun manageUnknownAppSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
