package com.example

import com.example.data.update.GitHubUpdateManager
import com.example.data.update.Updater
import com.example.data.update.VersionChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateManagerTest {

    @Test
    fun normalizeRepo_handlesUrlsAndPlaceholders() {
        assertEquals("4RetAppMaster/FitTracker", Updater.getConfiguredRepo())
        assertEquals("4RetAppMaster/FitTracker", VersionChecker.getConfiguredRepo())
        assertEquals("4RetAppMaster/FitTracker.", Updater.normalizeRepo("https://github.com/4RetAppMaster/FitTracker."))
        assertEquals("4RetAppMaster/FitTracker", Updater.normalizeRepo("https://github.com/4RetAppMaster/FitTracker"))
        assertEquals("sirmouke/FitTracker", Updater.normalizeRepo("https://github.com/sirmouke/FitTracker.git"))
        assertEquals("sirmouke/FitTracker", Updater.normalizeRepo("github.com/sirmouke/FitTracker/releases"))
        assertEquals("", Updater.normalizeRepo(""))
        assertEquals("", Updater.normalizeRepo("MY_GITHUB_REPO"))
        assertEquals("", GitHubUpdateManager.normalizeRepo("owner/repo"))
    }

    @Test
    fun extractVersionNumber_handlesTagsAndApkNames() {
        assertEquals("2.9", Updater.extractVersionNumber("v2.9"))
        assertEquals("2.9.1", Updater.extractVersionNumber("Release v2.9.1"))
        assertEquals("2.9", Updater.extractVersionNumber("FitTracker-v2.9.apk"))
        assertEquals("2.10", GitHubUpdateManager.extractVersionNumber("FitTracker-v2.10.apk"))
    }

    @Test
    fun isRemoteVersionNewer_comparesSemverProperly() {
        assertTrue(Updater.isRemoteVersionNewer("v2.9", "2.8"))
        assertTrue(Updater.isRemoteVersionNewer("2.10", "2.8"))
        assertTrue(Updater.isRemoteVersionNewer("2.8.1", "2.8"))
        assertTrue(Updater.isRemoteVersionNewer("FitTracker-v2.9.apk", "2.8"))
        assertFalse(Updater.isRemoteVersionNewer("v2.9", "2.9"))
        assertFalse(Updater.isRemoteVersionNewer("v2.8", "2.9"))
        assertFalse(GitHubUpdateManager.isRemoteVersionNewer("1.9.9", "2.9"))
    }
}
