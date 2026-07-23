package com.watercantracker.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progressPercent: Int) : DownloadState()
    data class Done(val filePath: String) : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}

/**
 * Downloads the update APK using Android's built-in DownloadManager (handles retries,
 * background downloading, and progress) then hands off to the package installer.
 */
@Singleton
class UpdateDownloader @Inject constructor() {

    private var downloadId: Long = -1

    fun downloadApk(context: Context, apkUrl: String, versionName: String) = callbackFlow {
        trySend(DownloadState.Downloading(0))

        val fileName = "WaterCanTracker_v$versionName.apk"
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Water Can Tracker update")
            .setDescription("Downloading version $versionName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val status = cursor.getInt(statusIdx)
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                                val localUri = cursor.getString(uriIdx)
                                trySend(DownloadState.Done(localUri ?: ""))
                            }
                            DownloadManager.STATUS_FAILED -> {
                                trySend(DownloadState.Failed("Download failed"))
                            }
                        }
                    }
                    cursor.close()
                    close()
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        awaitClose { context.unregisterReceiver(receiver) }
    }

    /** Opens the system package installer for the downloaded APK. */
    fun installApk(context: Context, versionName: String) {
        val fileName = "WaterCanTracker_v$versionName.apk"
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (!file.exists()) return

        val apkUri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }
}
