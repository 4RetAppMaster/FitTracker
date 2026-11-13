package com.example

import com.example.data.update.GitHubUpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateManagerTest {

    @Test
    fun normalizeRepo_handlesUrlsAndPlaceholders() {
        assertEquals("sirmouke/FitTracker", GitHubUpdateManager.normalizeRepo("sirmouke/FitTracker"))
        assertEquals("sirmouke/FitTracker", GitHubUpdateManager.normalizeRepo("https://github.com/sirmouke/FitTracker"))
        assertEquals("sirmouke/FitTracker", GitHubUpdateManager.normalizeRepo("https://github.com/sirmouke/FitTracker.git"))
        assertEquals("sirmouke/FitTracker", GitHubUpdateManager.normalizeRepo("github.com/sirmouke/FitTracker/releases"))
        assertEquals("", GitHubUpdateManager.normalizeRepo(""))
        assertEquals("", GitHubUpdateManager.normalizeRepo("MY_GITHUB_REPO"))
        assertEquals("", GitHubUpdateManager.normalizeRepo("owner/repo"))
    }

    @Test
    fun extractVersionNumber_handlesTagsAndApkNames() {
        assertEquals("2.4", GitHubUpdateManager.extractVersionNumber("v2.4"))
        assertEquals("2.4.1", GitHubUpdateManager.extractVersionNumber("Release v2.4.1"))
        assertEquals("2.4", GitHubUpdateManager.extractVersionNumber("FitTracker-v2.4.apk"))
        assertEquals("2.10", GitHubUpdateManager.extractVersionNumber("FitTracker-v2.10.apk"))
    }

    @Test
    fun isRemoteVersionNewer_comparesSemverProperly() {
        assertTrue(GitHubUpdateManager.isRemoteVersionNewer("v2.4", "2.3"))
        assertTrue(GitHubUpdateManager.isRemoteVersionNewer("2.10", "2.3"))
        assertTrue(GitHubUpdateManager.isRemoteVersionNewer("2.3.1", "2.3"))
        assertTrue(GitHubUpdateManager.isRemoteVersionNewer("FitTracker-v2.4.apk", "2.3"))
        assertFalse(GitHubUpdateManager.isRemoteVersionNewer("v2.3", "2.3"))
        assertFalse(GitHubUpdateManager.isRemoteVersionNewer("v2.2", "2.3"))
        assertFalse(GitHubUpdateManager.isRemoteVersionNewer("1.9.9", "2.3"))
    }
}
