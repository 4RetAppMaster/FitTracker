package com.example.data.update

import com.example.BuildConfig

/**
 * Compatibility delegator to [Updater] for release tag checking and version comparisons.
 */
object VersionChecker {

    fun getConfiguredRepo(): String = Updater.getConfiguredRepo()

    fun normalizeRepo(rawInput: String): String = Updater.normalizeRepo(rawInput)

    fun extractVersionNumber(raw: String): String = Updater.extractVersionNumber(raw)

    fun isRemoteVersionNewer(remoteRaw: String, currentRaw: String): Boolean =
        Updater.isRemoteVersionNewer(remoteRaw, currentRaw)

    suspend fun checkForUpdate(
        repoInput: String = Updater.getConfiguredRepo(),
        currentVersion: String = BuildConfig.VERSION_NAME
    ): UpdateCheckResult = Updater.checkForUpdate(repoInput, currentVersion)
}
