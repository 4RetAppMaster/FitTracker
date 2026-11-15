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
        assertEquals("4RetAppMaster/FitTracker", Updater.normalizeRepo("https://github.com/4RetAppMaster/FitTracker"))
        assertEquals("4RetAppMaster/FitTracker.", Updater.normalizeRepo("https://github.com/4RetAppMaster/FitTracker."))
        assertEquals("sirmouke/FitTracker", Updater.normalizeRepo("https://github.com/sirmouke/FitTracker.git"))
        assertEquals("sirmouke/FitTracker", Updater.normalizeRepo("github.com/sirmouke/FitTracker/releases"))
        assertEquals("", Updater.normalizeRepo(""))
        assertEquals("", Updater.normalizeRepo("MY_GITHUB_REPO"))
        assertEquals("", GitHubUpdateManager.normalizeRepo("owner/repo"))
    }

    @Test
    fun extractVersionNumber_handlesTagsAndApkNames() {
        assertEquals("2.11", Updater.extractVersionNumber("v2.11"))
        assertEquals("2.11.1", Updater.extractVersionNumber("Release v2.11.1"))
        assertEquals("2.11", Updater.extractVersionNumber("FitTracker-v2.11.apk"))
        assertEquals("2.11", GitHubUpdateManager.extractVersionNumber("FitTracker-v2.11.apk"))
    }

    @Test
    fun isRemoteVersionNewer_comparesSemverProperly() {
        assertTrue(Updater.isRemoteVersionNewer("v2.11", "2.10"))
        assertTrue(Updater.isRemoteVersionNewer("2.12", "2.11"))
        assertTrue(Updater.isRemoteVersionNewer("2.11.1", "2.11"))
        assertTrue(Updater.isRemoteVersionNewer("FitTracker-v2.11.apk", "2.10"))
        assertFalse(Updater.isRemoteVersionNewer("v2.11", "2.11"))
        assertFalse(Updater.isRemoteVersionNewer("v2.10", "2.11"))
        assertFalse(GitHubUpdateManager.isRemoteVersionNewer("1.9.9", "2.11"))
    }
}
