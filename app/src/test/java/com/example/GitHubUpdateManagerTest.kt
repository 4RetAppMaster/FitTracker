package com.example

import com.example.data.update.GitHubUpdateManager
import com.example.data.update.VersionChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateManagerTest {

    @Test
    fun normalizeRepo_handlesUrlsAndPlaceholders() {
        assertEquals("sirmouke/FitTracker", VersionChecker.normalizeRepo("sirmouke/FitTracker"))
        assertEquals("sirmouke/FitTracker", VersionChecker.normalizeRepo("https://github.com/sirmouke/FitTracker"))
        assertEquals("sirmouke/FitTracker", VersionChecker.normalizeRepo("https://github.com/sirmouke/FitTracker.git"))
        assertEquals("sirmouke/FitTracker", VersionChecker.normalizeRepo("github.com/sirmouke/FitTracker/releases"))
        assertEquals("", VersionChecker.normalizeRepo(""))
        assertEquals("", VersionChecker.normalizeRepo("MY_GITHUB_REPO"))
        assertEquals("", GitHubUpdateManager.normalizeRepo("owner/repo"))
    }

    @Test
    fun extractVersionNumber_handlesTagsAndApkNames() {
        assertEquals("2.6", VersionChecker.extractVersionNumber("v2.6"))
        assertEquals("2.6.1", VersionChecker.extractVersionNumber("Release v2.6.1"))
        assertEquals("2.6", VersionChecker.extractVersionNumber("FitTracker-v2.6.apk"))
        assertEquals("2.10", GitHubUpdateManager.extractVersionNumber("FitTracker-v2.10.apk"))
    }

    @Test
    fun isRemoteVersionNewer_comparesSemverProperly() {
        assertTrue(VersionChecker.isRemoteVersionNewer("v2.6", "2.5"))
        assertTrue(VersionChecker.isRemoteVersionNewer("2.10", "2.5"))
        assertTrue(VersionChecker.isRemoteVersionNewer("2.5.1", "2.5"))
        assertTrue(VersionChecker.isRemoteVersionNewer("FitTracker-v2.6.apk", "2.5"))
        assertFalse(VersionChecker.isRemoteVersionNewer("v2.6", "2.6"))
        assertFalse(VersionChecker.isRemoteVersionNewer("v2.5", "2.6"))
        assertFalse(GitHubUpdateManager.isRemoteVersionNewer("1.9.9", "2.6"))
    }
}
