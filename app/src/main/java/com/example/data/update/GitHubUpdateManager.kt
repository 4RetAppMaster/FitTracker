package com.example.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object GitHubUpdateManager {

    fun getConfiguredRepo(): String = VersionChecker.getConfiguredRepo()

    fun normalizeRepo(rawInput: String): String = VersionChecker.normalizeRepo(rawInput)

    fun extractVersionNumber(raw: String): String = VersionChecker.extractVersionNumber(raw)

    fun isRemoteVersionNewer(remoteRaw: String, currentRaw: String): Boolean =
        VersionChecker.isRemoteVersionNewer(remoteRaw, currentRaw)

    suspend fun checkForUpdate(
        repoInput: String = VersionChecker.getConfiguredRepo(),
        currentVersion: String = BuildConfig.VERSION_NAME
    ): UpdateCheckResult = VersionChecker.checkForUpdate(repoInput, currentVersion)

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float?) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            updatesDir.listFiles()?.forEach { file ->
                runCatching { file.delete() }
            }

            val tempFile = File(updatesDir, "FitTracker-update.apk.tmp")
            val finalFile = File(updatesDir, "FitTracker-update.apk")

            var currentUrl = downloadUrl
            var connection: HttpURLConnection? = null
            var redirects = 0

            while (redirects < 6) {
                val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "FitTracker-Android/${BuildConfig.VERSION_NAME}")
                    setRequestProperty("Accept", "application/octet-stream")
                }
                val code = conn.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val nextLocation = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (nextLocation.isNullOrBlank()) {
                        return@withContext Result.failure(IllegalStateException("Empty redirect URL"))
                    }
                    currentUrl = URL(URL(currentUrl), nextLocation).toString()
                    redirects++
                } else {
                    connection = conn
                    break
                }
            }

            val activeConn = connection ?: return@withContext Result.failure(
                IllegalStateException("Too many redirects while downloading APK")
            )

            try {
                if (activeConn.responseCode !in 200..299) {
                    return@withContext Result.failure(
                        IllegalStateException("Download failed with HTTP ${activeConn.responseCode}")
                    )
                }

                val totalBytes = activeConn.contentLengthLong
                BufferedInputStream(activeConn.inputStream, 16 * 1024).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastReportedPct = -1

                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0L) {
                                val pct = ((downloaded * 100L) / totalBytes).toInt()
                                if (pct != lastReportedPct) {
                                    lastReportedPct = pct
                                    onProgress((downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                                }
                            } else {
                                onProgress(null)
                            }
                        }
                        output.flush()
                    }
                }
            } finally {
                activeConn.disconnect()
            }

            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            Result.success(finalFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun createUnknownSourcesIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Result<Unit> {
        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openReleasePage(context: Context, url: String): Result<Unit> {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
