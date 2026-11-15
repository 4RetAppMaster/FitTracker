package com.example.data.update

import android.content.Context
import android.content.Intent
import com.example.BuildConfig
import java.io.File

/**
 * Compatibility delegator to [Updater] for APK downloads and installation intents.
 */
object GitHubUpdateManager {

    fun getConfiguredRepo(): String = Updater.getConfiguredRepo()

    fun normalizeRepo(rawInput: String): String = Updater.normalizeRepo(rawInput)

    fun extractVersionNumber(raw: String): String = Updater.extractVersionNumber(raw)

    fun isRemoteVersionNewer(remoteRaw: String, currentRaw: String): Boolean =
        Updater.isRemoteVersionNewer(remoteRaw, currentRaw)

    suspend fun checkForUpdate(
        repoInput: String = Updater.getConfiguredRepo(),
        currentVersion: String = BuildConfig.VERSION_NAME
    ): UpdateCheckResult = Updater.checkForUpdate(repoInput, currentVersion)

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float?) -> Unit
    ): Result<File> = Updater.downloadApk(context, downloadUrl, onProgress)

    fun canInstallPackages(context: Context): Boolean = Updater.canInstallPackages(context)

    fun createUnknownSourcesIntent(context: Context): Intent = Updater.createUnknownSourcesIntent(context)

    fun launchApkInstaller(context: Context, apkFile: File): Result<Unit> =
        Updater.launchApkInstaller(context, apkFile)

    fun openReleasePage(context: Context, url: String): Result<Unit> =
        Updater.openReleasePage(context, url)
}
