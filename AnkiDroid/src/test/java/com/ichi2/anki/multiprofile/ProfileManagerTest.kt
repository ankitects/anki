/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multiprofile

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionHelper.PREF_COLLECTION_PATH
import com.ichi2.anki.multiprofile.ProfileManager.Companion.KEY_LAST_ACTIVE_PROFILE_ID
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PROFILE_REGISTRY_FILENAME
import com.ichi2.anki.preferences.sharedPrefs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProfileManagerTest {
    private lateinit var context: Context

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs.edit(commit = true) { clear() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initialize sets up Default profile if none exists`() {
        ProfileManager.create(context)

        assertEquals("default", prefs.getString(KEY_LAST_ACTIVE_PROFILE_ID, null))
    }

    @Test
    fun `Existing Legacy user is automatically migrated to Default profile`() {
        File(context.filesDir, "old_user_data.txt").apply { writeText("Important Data") }

        val manager = ProfileManager.create(context)

        assertEquals("default", prefs.getString(KEY_LAST_ACTIVE_PROFILE_ID, null))

        val activeContext = manager.activeProfileContext as ProfileContextWrapper
        val currentFile = File(activeContext.filesDir, "old_user_data.txt")

        assertTrue("Default profile must see legacy files", currentFile.exists())
        assertEquals("Important Data", currentFile.readText())
        assertEquals("Path must match root filesDir", context.filesDir.absolutePath, activeContext.filesDir.absolutePath)
    }

    @Test
    fun `Manager loads existing previously active profile on restart`() {
        val aliceId = "p_alice"

        prefs.edit(commit = true) {
            putString(KEY_LAST_ACTIVE_PROFILE_ID, aliceId)
            putString(aliceId, "Alice")
        }

        val manager = ProfileManager.create(context)

        val activeContext = manager.activeProfileContext
        assertTrue(activeContext.filesDir.absolutePath.contains(aliceId))
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.P)
    fun `New profile sets WebView directory suffix (API 28+)`() {
        val newId = "p_webview_test"
        prefs.edit(commit = true) { putString(KEY_LAST_ACTIVE_PROFILE_ID, newId) }

        mockkStatic(WebView::class)
        every { WebView.setDataDirectorySuffix(any()) } just runs

        ProfileManager.create(context)

        verify(exactly = 1) { WebView.setDataDirectorySuffix(newId) }
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.P)
    fun `Default profile does not set WebView directory suffix (API 28+)`() {
        mockkStatic(WebView::class)
        every { WebView.setDataDirectorySuffix(any()) } just runs

        ProfileManager.create(context)

        verify(exactly = 0) { WebView.setDataDirectorySuffix(any()) }
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.P)
    fun `Swallows exception if WebView is already initialized`() {
        val newId = "p_error_test"
        prefs.edit(commit = true) { putString(KEY_LAST_ACTIVE_PROFILE_ID, newId) }

        mockkStatic(WebView::class)
        every { WebView.setDataDirectorySuffix(any()) } throws IllegalStateException("Already initialized")

        try {
            ProfileManager.create(context)
        } catch (e: Exception) {
            throw AssertionError("Manager should have caught the WebView exception but didn't", e)
        }

        verify(exactly = 1) { WebView.setDataDirectorySuffix(newId) }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `Legacy device clears cookies on init (Pre-API 28)`() {
        mockkStatic(CookieManager::class)
        val mockCookies = mockk<CookieManager>(relaxed = true)
        every { CookieManager.getInstance() } returns mockCookies
        ProfileManager.create(context)

        verify(exactly = 1) { mockCookies.removeAllCookies(null) }
    }

    @Test
    fun `ProfileId DEFAULT must be strictly 'default' to preserve legacy compatibility`() {
        assertEquals("default", ProfileId.DEFAULT.value)
    }

    @Test
    fun `ProfileMetadata JSON round-trip preserves all fields`() {
        val original =
            ProfileManager.ProfileMetadata(
                displayName = ProfileName.fromTrustedSource("Test User"),
                version = 5,
                createdTimestamp = "2025-12-31T23:59:59Z",
            )

        val jsonString = original.toJson()
        val reconstructed = ProfileManager.ProfileMetadata.fromJson(jsonString)

        assertEquals("Serialization round-trip failed!", original, reconstructed)
    }

    @Test
    fun `getAllProfiles returns all registered profiles`() {
        val manager = ProfileManager.create(context)

        val profile1 = manager.createNewProfile(ProfileName.fromTrustedSource("Work"))
        val profile2 = manager.createNewProfile(ProfileName.fromTrustedSource("Personal"))

        val allProfiles = manager.getAllProfiles()

        assertEquals(3, allProfiles.size)
        assertTrue(allProfiles.containsKey(ProfileId.DEFAULT))
        assertTrue(allProfiles.containsKey(profile1))
        assertTrue(allProfiles.containsKey(profile2))
        assertEquals("Default", allProfiles[ProfileId.DEFAULT]?.displayName?.value)
        assertEquals("Work", allProfiles[profile1]?.displayName?.value)
        assertEquals("Personal", allProfiles[profile2]?.displayName?.value)
    }

    @Test
    fun `getAllProfiles returns only default when no profiles created`() {
        ProfileManager.create(context)

        val allProfiles = ProfileManager.create(context).getAllProfiles()

        assertEquals(1, allProfiles.size)
        assertTrue(allProfiles.containsKey(ProfileId.DEFAULT))
    }

    @Test
    fun `renameProfile updates displayName in registry`() {
        val manager = ProfileManager.create(context)
        val profileId = manager.createNewProfile(ProfileName.fromTrustedSource("Original Name"))
        val newName = ProfileName.fromTrustedSource("Updated Name")

        manager.renameProfile(profileId, newName)

        val json = prefs.getString(profileId.value, null)
        val metadata = ProfileManager.ProfileMetadata.fromJson(json!!)

        assertEquals(newName, metadata.displayName)
    }

    @Test
    fun `renameProfile preserves version and createdTimestamp`() {
        val manager = ProfileManager.create(context)
        val profileId = manager.createNewProfile(ProfileName.fromTrustedSource("Original Name"))

        val originalJson = prefs.getString(profileId.value, null)
        val originalMetadata = ProfileManager.ProfileMetadata.fromJson(originalJson!!)

        manager.renameProfile(profileId, ProfileName.fromTrustedSource("New Name"))

        val updatedJson = prefs.getString(profileId.value, null)
        val updatedMetadata = ProfileManager.ProfileMetadata.fromJson(updatedJson!!)

        assertEquals("Version must be preserved", originalMetadata.version, updatedMetadata.version)
        assertEquals(
            "Timestamp must be preserved",
            originalMetadata.createdTimestamp,
            updatedMetadata.createdTimestamp,
        )
    }

    @Test
    fun `loading a non-default profile sets deckPath to the profile-specific external dir`() {
        val manager = ProfileManager.create(context)
        val ashishId = manager.createNewProfile(ProfileName.fromTrustedSource("Ashish"))
        with(ProfileManager.ProfileSwitchContext) { manager.switchActiveProfile(ashishId) }

        val reloaded = ProfileManager.create(context)
        val deckPath = reloaded.activeProfileContext.sharedPrefs().getString(PREF_COLLECTION_PATH, null)

        val expected = File(context.getExternalFilesDir(null), ashishId.value).absolutePath
        assertEquals(expected, deckPath)
    }

    @Test
    fun `loading a non-default profile creates the deckPath directory on disk`() {
        val manager = ProfileManager.create(context)
        val ashishId = manager.createNewProfile(ProfileName.fromTrustedSource("Ashish"))
        with(ProfileManager.ProfileSwitchContext) { manager.switchActiveProfile(ashishId) }

        val reloaded = ProfileManager.create(context)
        val deckPath = reloaded.activeProfileContext.sharedPrefs().getString(PREF_COLLECTION_PATH, null)!!

        assertTrue("deckPath directory must exist after profile load", File(deckPath).isDirectory)
    }

    @Test
    fun `renameProfile does not write to disk if name is identical`() {
        val manager = ProfileManager.create(context)
        val name = ProfileName.fromTrustedSource("No Change")
        val profileId = manager.createNewProfile(name)

        val originalJson = prefs.getString(profileId.value, null)

        manager.renameProfile(profileId, name)

        val currentJson = prefs.getString(profileId.value, null)
        assertEquals("No disk write should occur for identical names", originalJson, currentJson)
    }

    @Test
    fun `renameProfile throws IllegalArgumentException for missing profile`() {
        val manager = ProfileManager.create(context)
        val fakeId = ProfileId("p_ghost")

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                manager.renameProfile(fakeId, ProfileName.fromTrustedSource("New Name"))
            }

        assertTrue(exception.message!!.contains("not found"))
    }

    @Test
    fun `reloading an existing profile does not overwrite a pre-existing deckPath`() {
        val manager = ProfileManager.create(context)
        val newId = manager.createNewProfile(ProfileName.fromTrustedSource("Work"))
        with(ProfileManager.ProfileSwitchContext) { manager.switchActiveProfile(newId) }

        // First load materializes deckPath. Then the user "relocates" their collection.
        val firstLoad = ProfileManager.create(context)
        val userChosenPath = File(context.filesDir, "user_relocated").apply { mkdirs() }.absolutePath
        firstLoad.activeProfileContext.sharedPrefs().edit(commit = true) {
            putString(PREF_COLLECTION_PATH, userChosenPath)
        }

        // Simulate app restart - ProfileManager.create runs again.
        val reloaded = ProfileManager.create(context)
        val deckPath = reloaded.activeProfileContext.sharedPrefs().getString(PREF_COLLECTION_PATH, null)

        assertEquals("User-relocated deckPath must not be overwritten on reload", userChosenPath, deckPath)
    }
}
