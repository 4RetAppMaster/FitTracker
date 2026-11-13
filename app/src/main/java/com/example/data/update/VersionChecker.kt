package com.example.data.update

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GitHubUpdateInfo(
    val latestVersion: String,
    val releaseTag: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val releaseHtmlUrl: String? = null
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val info: GitHubUpdateInfo) : UpdateCheckResult()
    data class UpToDate(val latestTag: String? = null) : UpdateCheckResult()
    data class Error(val message: String, val isUnconfigured: Boolean = false) : UpdateCheckResult()
}

/**
 * Service responsible for querying the GitHub Releases/Tags API for the latest release tag
 * and comparing it against the locally installed [BuildConfig.VERSION_NAME].
 */
object VersionChecker {

    // Fixed GitHub repository ("owner/repo"). Automatically populated from BuildConfig.GITHUB_REPO
    // during GitHub Actions builds (${{ github.repository }}) or via .env / AI Studio Secrets.
    private const val FIXED_GITHUB_REPO = ""

    fun getConfiguredRepo(): String {
        val fromBuildConfig = normalizeRepo(BuildConfig.GITHUB_REPO)
        if (fromBuildConfig.isNotEmpty()) return fromBuildConfig
        return normalizeRepo(FIXED_GITHUB_REPO)
    }

    fun normalizeRepo(rawInput: String): String {
        var cleaned = rawInput.trim()
        if (
            cleaned.isEmpty() ||
            cleaned.equals("MY_GITHUB_REPO", ignoreCase = true) ||
            cleaned.equals("owner/repo", ignoreCase = true)
        ) {
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
        repoInput: String = getConfiguredRepo(),
        currentVersion: String = BuildConfig.VERSION_NAME
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val repo = normalizeRepo(repoInput)
        if (repo.isEmpty()) {
            return@withContext UpdateCheckResult.Error(
                message = "Update repository is not linked in this build.",
                isUnconfigured = true
            )
        }

        try {
            // 1. Fetch latest published GitHub Release tag
            val latestResponse = httpGetJson("https://api.github.com/repos/$repo/releases/latest")
            if (latestResponse.statusCode == 200) {
                val releaseJson = JSONObject(latestResponse.body)
                val tagName = releaseJson.optString("tag_name", "").ifBlank {
                    releaseJson.optString("name", "")
                }
                if (tagName.isNotBlank() && isRemoteVersionNewer(tagName, currentVersion)) {
                    val info = buildReleaseUpdateInfo(repo, releaseJson, currentVersion)
                    return@withContext UpdateCheckResult.UpdateAvailable(info)
                }
                if (tagName.isNotBlank() && !isRemoteVersionNewer(tagName, currentVersion)) {
                    checkRepoRootApkForUpdate(repo, currentVersion)?.let { rootInfo ->
                        return@withContext UpdateCheckResult.UpdateAvailable(rootInfo)
                    }
                    return@withContext UpdateCheckResult.UpToDate(latestTag = tagName)
                }
            }

            // 2. Fallback: fetch /releases?per_page=1 (covers pre-releases)
            val listResponse = httpGetJson("https://api.github.com/repos/$repo/releases?per_page=1")
            if (listResponse.statusCode == 200) {
                val arr = JSONArray(listResponse.body)
                if (arr.length() > 0) {
                    val firstRelease = arr.getJSONObject(0)
                    val tagName = firstRelease.optString("tag_name", "").ifBlank {
                        firstRelease.optString("name", "")
                    }
                    if (tagName.isNotBlank() && isRemoteVersionNewer(tagName, currentVersion)) {
                        val info = buildReleaseUpdateInfo(repo, firstRelease, currentVersion)
                        return@withContext UpdateCheckResult.UpdateAvailable(info)
                    }
                    if (tagName.isNotBlank()) {
                        checkRepoRootApkForUpdate(repo, currentVersion)?.let { rootInfo ->
                            return@withContext UpdateCheckResult.UpdateAvailable(rootInfo)
                        }
                        return@withContext UpdateCheckResult.UpToDate(latestTag = tagName)
                    }
                }
            }

            // 3. Fallback: check APK files uploaded directly to repo root
            checkRepoRootApkForUpdate(repo, currentVersion)?.let { rootInfo ->
                return@withContext UpdateCheckResult.UpdateAvailable(rootInfo)
            }

            // 4. Fallback: check Git tags directly (if tags exist without a GitHub Release object)
            val tagsResponse = httpGetJson("https://api.github.com/repos/$repo/tags?per_page=5")
            if (tagsResponse.statusCode == 200) {
                val tags = JSONArray(tagsResponse.body)
                var newestTag: String? = null
                for (i in 0 until tags.length()) {
                    val tagName = tags.optJSONObject(i)?.optString("name", "").orEmpty()
                    if (tagName.isNotBlank() && isRemoteVersionNewer(tagName, currentVersion)) {
                        if (newestTag == null || isRemoteVersionNewer(tagName, newestTag)) {
                            newestTag = tagName
                        }
                    }
                }
                if (newestTag != null) {
                    return@withContext UpdateCheckResult.UpdateAvailable(
                        GitHubUpdateInfo(
                            latestVersion = extractVersionNumber(newestTag),
                            releaseTag = newestTag,
                            releaseNotes = "",
                            apkDownloadUrl = null,
                            apkFileName = null,
                            releaseHtmlUrl = "https://github.com/$repo/releases/tag/$newestTag"
                        )
                    )
                }
                if (tags.length() > 0) {
                    return@withContext UpdateCheckResult.UpToDate(
                        latestTag = tags.optJSONObject(0)?.optString("name", "")?.ifBlank { null }
                    )
                }
            }

            if (latestResponse.statusCode in listOf(200, 404) || listResponse.statusCode == 200) {
                UpdateCheckResult.UpToDate()
            } else {
                UpdateCheckResult.Error("GitHub returned HTTP ${latestResponse.statusCode}")
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.localizedMessage ?: "Failed to check for updates")
        }
    }

    private fun buildReleaseUpdateInfo(
        repo: String,
        releaseJson: JSONObject,
        currentVersion: String
    ): GitHubUpdateInfo {
        val tagName = releaseJson.optString("tag_name", "")
        val releaseName = releaseJson.optString("name", "")
        val rawVersion = tagName.ifBlank { releaseName }
        val assets = releaseJson.optJSONArray("assets")

        var bestAssetUrl: String? = null
        var bestAssetName: String? = null

        if (assets != null) {
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
        }

        // If release has no APK asset attached, check if a newer APK exists in the repo root
        if (bestAssetUrl == null) {
            checkRepoRootApkForUpdate(repo, currentVersion)?.let { rootApk ->
                bestAssetUrl = rootApk.apkDownloadUrl
                bestAssetName = rootApk.apkFileName
            }
        }

        return GitHubUpdateInfo(
            latestVersion = extractVersionNumber(rawVersion),
            releaseTag = rawVersion,
            releaseNotes = releaseJson.optString("body", "").trim(),
            apkDownloadUrl = bestAssetUrl,
            apkFileName = bestAssetName,
            releaseHtmlUrl = releaseJson.optString("html_url", "").ifBlank { null }
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
                            releaseTag = "v$versionInName",
                            releaseNotes = "Updated APK ($name)",
                            apkDownloadUrl = downloadUrl,
                            apkFileName = name,
                            releaseHtmlUrl = "https://github.com/$repo/releases"
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
}
