package com.watercantracker.app.update

import com.watercantracker.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: String
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * Checks a small JSON manifest hosted in the GitHub repo for a newer version.
 *
 * Host a file at this raw URL (edit VERSION_MANIFEST_URL to match your repo):
 *   https://raw.githubusercontent.com/<user>/<repo>/main/version.json
 *
 * Example version.json contents:
 * {
 *   "versionCode": 4,
 *   "versionName": "1.4.0",
 *   "apkUrl": "https://github.com/<user>/<repo>/releases/download/v1.4.0/app-release.apk",
 *   "changelog": "- Added in-app updater\n- Fixed rotation bug"
 * }
 *
 * Update this file each time you cut a new release so the app can detect it.
 */
@Singleton
class UpdateChecker @Inject constructor() {

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(VERSION_MANIFEST_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateCheckResult.Error("Server returned $responseCode")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val info = UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl      = json.getString("apkUrl"),
                changelog   = json.optString("changelog", "")
            )

            if (info.versionCode > BuildConfig.VERSION_CODE) {
                UpdateCheckResult.UpdateAvailable(info)
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Unknown error checking for updates")
        }
    }

    companion object {
        // EDIT THIS to point at your own repo's raw version.json
        const val VERSION_MANIFEST_URL =
            "https://raw.githubusercontent.com/deniljoseph/water-can/main/version.json"
    }
}
