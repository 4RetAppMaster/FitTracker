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
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class GitHubUpdateInfo(
    val latestVersion: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: GitHubUpdateInfo) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

object GitHubUpdateManager {

    private const val PREFS_NAME = "app_update_prefs"
    private const val KEY_GITHUB_REPO = "github_repo"

    fun getConfiguredRepo(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_GITHUB_REPO, null)?.trim().orEmpty()
        if (saved.isNotEmpty()) return normalizeRepo(saved)
        return normalizeRepo(BuildConfig.GITHUB_REPO)
    }

    fun saveConfiguredRepo(context: Context, rawInput: String): String {
        val normalized = normalizeRepo(rawInput)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GITHUB_REPO, normalized).apply()
        return normalized
    }

    fun normalizeRepo(rawInput: String): String {
        var cleaned = rawInput.trim()
        if (cleaned.isEmpty() || cleaned.equals("MY_GITHUB_REPO", ignoreCase = true) || cleaned.equals("owner/repo", ignoreCase = true)) {
            return ""
        }
        cleaned = cleaned
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
        val parts = cleaned.split("/").filter { it.isNotBlank() }
        return if (parts.size >= 2) "${parts[0]}/${parts[1]}" else ""
    }

    fun extractVersionNumber(raw: String): String {
        val multiPart = Regex("""(\d+(?:\.\d+)+)""").find(raw)?.groupValues?.getOrNull(1)
        if (multiPart != null) return multiPart
        val singlePart = Regex("""v?(\d+)""", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.getOrNull(1)
        return singlePart ?: raw.trim()
    }

    fun isRemoteVersionNewer(remoteRaw: String, currentRaw: String): Boolean {
        val remoteParts = extractVersionNumber(remoteRaw).split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = extractVersionNumber(currentRaw).split(".").mapNotNull { it.toIntOrNull() }
        if (remoteParts.isEmpty() || currentParts.isEmpty()) return false

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    suspend fun checkForUpdate(
        repoInput: String,
        currentVersion: String = BuildConfig.VERSION_NAME
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val repo = normalizeRepo(repoInput)
        if (repo.isEmpty()) {
            return@withContext UpdateCheckResult.Error("No GitHub repository configured.")
        }

        try {
            // 1. Try latest GitHub Release
            val latestResponse = httpGetJson("https://api.github.com/repos/$repo/releases/latest")
            if (latestResponse.statusCode == 200) {
                val releaseJson = JSONObject(latestResponse.body)
                parseReleaseForUpdate(releaseJson, currentVersion)?.let { info ->
                    return@withContext UpdateCheckResult.UpdateAvailable(info)
                }
                // Release found, check if same/older
                val tag = releaseJson.optString("tag_name", "")
                if (tag.isNotBlank() && !isRemoteVersionNewer(tag, currentVersion)) {
                    // Still check root APKs just in case user pushed APK file directly without creating a new release
                    checkRepoRootApkForUpdate(repo, currentVersion)?.let { rootInfo ->
                        return@withContext UpdateCheckResult.UpdateAvailable(rootInfo)
                    }
                    return@withContext UpdateCheckResult.UpToDate
                }
            }

            // 2. Fallback: try /releases?per_page=1 (catches pre-releases)
            val listResponse = httpGetJson("https://api.github.com/repos/$repo/releases?per_page=1")
            if (listResponse.statusCode == 200) {
                val arr = JSONArray(listResponse.body)
                if (arr.length() > 0) {
                    val firstRelease = arr.getJSONObject(0)
                    parseReleaseForUpdate(firstRelease, currentVersion)?.let { info ->
                        return@withContext UpdateCheckResult.UpdateAvailable(info)
                    }
                }
            }

            // 3. Fallback: check APK files pushed directly to repo root (e.g. FitTracker-v2.4.apk)
            checkRepoRootApkForUpdate(repo, currentVersion)?.let { rootInfo ->
                return@withContext UpdateCheckResult.UpdateAvailable(rootInfo)
            }

            if (latestResponse.statusCode in listOf(200, 404) || listResponse.statusCode == 200) {
                UpdateCheckResult.UpToDate
            } else {
                UpdateCheckResult.Error("GitHub returned HTTP ${latestResponse.statusCode}")
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.localizedMessage ?: "Failed to check GitHub updates")
        }
    }

    private fun parseReleaseForUpdate(releaseJson: JSONObject, currentVersion: String): GitHubUpdateInfo? {
        val tagName = releaseJson.optString("tag_name", "")
        val releaseName = releaseJson.optString("name", "")
        val rawVersion = tagName.ifBlank { releaseName }
        if (rawVersion.isBlank() || !isRemoteVersionNewer(rawVersion, currentVersion)) {
            return null
        }

        val assets = releaseJson.optJSONArray("assets") ?: return null
        var bestAssetUrl: String? = null
        var bestAssetName: String? = null

        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            val url = asset.optString("browser_download_url", "")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                bestAssetUrl = url
                bestAssetName = name
                if (name.contains("FitTracker", ignoreCase = true)) {
                    break
                }
            }
        }

        if (bestAssetUrl == null || bestAssetName == null) return null
        return GitHubUpdateInfo(
            latestVersion = extractVersionNumber(rawVersion),
            releaseNotes = releaseJson.optString("body", "").trim(),
            apkDownloadUrl = bestAssetUrl,
            apkFileName = bestAssetName
        )
    }

    private fun checkRepoRootApkForUpdate(repo: String, currentVersion: String): GitHubUpdateInfo? {
        val response = httpGetJson("https://api.github.com/repos/$repo/contents/")
        if (response.statusCode != 200) return null

        val files = JSONArray(response.body)
        var bestInfo: GitHubUpdateInfo? = null

        for (i in 0 until files.length()) {
            val item = files.optJSONObject(i) ?: continue
            val name = item.optString("name", "")
            val downloadUrl = item.optString("download_url", "")
            if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                val versionInName = extractVersionNumber(name)
                if (versionInName.any { it.isDigit() } && isRemoteVersionNewer(versionInName, currentVersion)) {
                    if (bestInfo == null || isRemoteVersionNewer(versionInName, bestInfo.latestVersion)) {
                        bestInfo = GitHubUpdateInfo(
                            latestVersion = versionInName,
                            releaseNotes = "Updated APK from GitHub repository ($name)",
                            apkDownloadUrl = downloadUrl,
                            apkFileName = name
                        )
                    }
                }
            }
        }
        return bestInfo
    }

    private data class HttpResponse(val statusCode: Int, val body: String)

    private fun httpGetJson(urlStr: String): HttpResponse {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "FitTracker-Android/${BuildConfig.VERSION_NAME}")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            HttpResponse(code, body)
        } finally {
            conn.disconnect()
        }
    }

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
}
