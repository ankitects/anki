// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper.PREF_COLLECTION_PATH
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PREF_LAST_ACTIVE_PROFILE_ID
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PREF_WEBVIEW_PROFILE_ID
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PROFILE_REGISTRY_FILENAME
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class ProfileManagerTest {
    private lateinit var context: Context
    private val appDataRoot: File by lazy { context.filesDir.parentFile!! }

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
        ProfileManager.resetForTesting()
    }

    @Test
    fun `initialize sets up Default profile if none exists`() {
        ProfileManager.create(context)

        assertEquals("default", prefs.getString(PREF_LAST_ACTIVE_PROFILE_ID, null))
    }

    @Test
    fun `createOrNull returns a manager on the active profile context`() {
        val manager = assertNotNull(ProfileManager.createOrNull(context))

        assertNull(ProfileManager.attachError)
        assertEquals(context.filesDir.absolutePath, manager.activeProfileContext.filesDir.absolutePath)
    }

    @Test
    fun `createOrNull initializes the Default profile on first run`() {
        ProfileManager.createOrNull(context)

        assertEquals("default", prefs.getString(PREF_LAST_ACTIVE_PROFILE_ID, null))
    }

    @Test
    fun `createOrNull returns null when the profile environment cannot be loaded`() {
        val unusableStorage =
            object : ContextWrapper(context) {
                override fun getApplicationContext(): Context? = null

                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ): SharedPreferences = throw IllegalStateException("storage unavailable")
            }

        val manager = ProfileManager.createOrNull(unusableStorage)

        assertNull(manager, "must not brick startup")
        assertNotNull(ProfileManager.attachError, "Failure must be recorded for later logging")
        assertNull(ProfileManager.consumeLoadFailure(), "no profile name to report")
    }

    @Test
    fun `a profile that fails to load is reported by name once`() {
        registerUnloadableProfile()

        assertNull(ProfileManager.createOrNull(context))

        assertEquals(loadFailure("Work", "Home"), ProfileManager.consumeLoadFailure())
        assertNull(ProfileManager.consumeLoadFailure(), "reported once per start")
    }

    @Test
    fun `a reported failure stays known until the next start`() {
        registerUnloadableProfile()
        ProfileManager.createOrNull(context)

        ProfileManager.consumeLoadFailure()

        assertEquals(loadFailure("Work", "Home"), ProfileManager.loadFailure)
    }

    @Test
    fun `choosing Default after a failed load opens Default on the next start`() {
        registerUnloadableProfile()
        ProfileManager.createOrNull(context)

        assertTrue(ProfileManager.selectDefaultProfile(context))

        assertEquals(ProfileId.DEFAULT, ProfileManager.createOrNull(context)?.activeProfileContext?.profileId)
    }

    @Test
    fun `choosing Default reports a registry that is not saved`() {
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } returns editor
        every { editor.commit() } returns false
        val unsaved =
            object : ContextWrapper(context) {
                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ): SharedPreferences = mockk { every { edit() } returns editor }
            }

        assertFalse(ProfileManager.selectDefaultProfile(unsaved))
    }

    @Test
    fun `a profile that opens again clears the earlier failure`() {
        val work = registerUnloadableProfile()
        ProfileManager.createOrNull(context)

        File(appDataRoot, work.value).delete()

        assertEquals(work, ProfileManager.createOrNull(context)?.activeProfileContext?.profileId)
        assertNull(ProfileManager.loadFailure)
        assertNull(ProfileManager.consumeLoadFailure())
    }

    @Test
    fun `a Default profile that fails to load is not reported`() {
        mockkObject(ProfileContextWrapper)
        every { ProfileContextWrapper.create(any(), any(), any()) } throws IOException("storage unavailable")

        assertNull(ProfileManager.createOrNull(context))

        assertNull(ProfileManager.consumeLoadFailure(), "Default is what AnkiDroid falls back to")
    }

    @Test
    fun `a profile missing from the registry is reported by its ID`() {
        ProfileManager.create(context)
        val missing = ProfileId("p_0000beef")
        File(appDataRoot, missing.value).writeText("a file where the profile folder belongs")
        prefs.edit(commit = true) { putString(PREF_LAST_ACTIVE_PROFILE_ID, missing.value) }

        assertNull(ProfileManager.createOrNull(context))

        assertEquals(
            loadFailure(missing.value, "Default"),
            ProfileManager.consumeLoadFailure(),
        )
    }

    private fun loadFailure(
        profileName: String,
        defaultProfileName: String,
    ) = ProfileManager.LoadFailure(ProfileName.fromTrustedSource(profileName), ProfileName.fromTrustedSource(defaultProfileName))

    private fun registerUnloadableProfile(): ProfileId {
        val manager = ProfileManager.create(context)
        manager.renameProfile(ProfileId.DEFAULT, ProfileName.fromTrustedSource("Home"))
        val work = manager.createNewProfile(ProfileName.fromTrustedSource("Work"))
        File(appDataRoot, work.value).writeText("a file where the profile folder belongs")
        prefs.edit(commit = true) { putString(PREF_LAST_ACTIVE_PROFILE_ID, work.value) }
        return work
    }

    @Test
    fun `create works before the application context is available`() {
        val duringAttachBaseContext =
            object : ContextWrapper(context) {
                override fun getApplicationContext(): Context? = null
            }

        val manager = ProfileManager.create(duringAttachBaseContext)

        assertEquals("default", prefs.getString(PREF_LAST_ACTIVE_PROFILE_ID, null))
        assertEquals(context.filesDir.absolutePath, manager.activeProfileContext.filesDir.absolutePath)
    }

    @Test
    fun `Existing Legacy user is automatically migrated to Default profile`() {
        File(context.filesDir, "old_user_data.txt").apply { writeText("Important Data") }

        val manager = ProfileManager.create(context)

        assertEquals("default", prefs.getString(PREF_LAST_ACTIVE_PROFILE_ID, null))

        val activeContext = manager.activeProfileContext
        val currentFile = File(activeContext.filesDir, "old_user_data.txt")

        assertTrue("Default profile must see legacy files", currentFile.exists())
        assertEquals("Important Data", currentFile.readText())
        assertEquals("Path must match root filesDir", context.filesDir.absolutePath, activeContext.filesDir.absolutePath)
    }

    @Test
    fun `Manager loads existing previously active profile on restart`() {
        val aliceId = "p_alice"

        prefs.edit(commit = true) {
            putString(PREF_LAST_ACTIVE_PROFILE_ID, aliceId)
            putString(aliceId, "Alice")
        }

        val manager = ProfileManager.create(context)

        val activeContext = manager.activeProfileContext
        assertTrue(activeContext.filesDir.absolutePath.contains(aliceId))
    }

    @Test
    fun `wrap leaves Default contexts alone`() {
        val manager = ProfileManager.create(context)
        val activityBase = ContextWrapper(context)

        assertSame(activityBase, manager.wrap(activityBase))
    }

    @Test
    fun `wrap routes a context into the active profile`() {
        prefs.edit(commit = true) { putString(PREF_LAST_ACTIVE_PROFILE_ID, "p_wrap") }
        val manager = ProfileManager.create(context)
        val profileDir = File(appDataRoot, "p_wrap").apply { deleteRecursively() }

        val wrapped = manager.wrap(ContextWrapper(context))

        assertFalse("wrap must not touch the disk", profileDir.exists())
        wrapped.getSharedPreferences("settings", Context.MODE_PRIVATE).edit(commit = true) { putString("key", "value") }
        assertTrue(File(appDataRoot, "shared_prefs/profile_p_wrap_settings.xml").exists())
        assertTrue(wrapped.filesDir.startsWith(profileDir))
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.P)
    fun `New profile sets WebView directory suffix (API 28+)`() {
        val newId = "p_webview_test"
        prefs.edit(commit = true) { putString(PREF_LAST_ACTIVE_PROFILE_ID, newId) }

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
        prefs.edit(commit = true) { putString(PREF_LAST_ACTIVE_PROFILE_ID, newId) }

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
    fun `fallback to Default must not expose another profile's cookies`() {
        val manager = ProfileManager.create(context)
        val previous = manager.createNewProfile(ProfileName.fromTrustedSource("Alice"))
        val unloadable = manager.createNewProfile(ProfileName.fromTrustedSource("Bob"))
        val cookies = CookieManager.getInstance()
        val url = "https://ankiweb.net/"
        cookies.setCookie(url, "session=alice")
        assertEquals("session=alice", cookies.getCookie(url))
        prefs.edit(commit = true) {
            putString(PREF_WEBVIEW_PROFILE_ID, previous.value)
            putString(PREF_LAST_ACTIVE_PROFILE_ID, unloadable.value)
        }
        File(appDataRoot, unloadable.value).writeText("a file where the profile folder belongs")

        assertNull(ProfileManager.createOrNull(context))

        // AnkiDroidApp uses its unwrapped Default context when createOrNull returns null.
        assertNull(cookies.getCookie(url), "Default must not inherit Alice's authenticated WebView session")
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `Legacy device keeps cookies on first run as the WebView data is already Default's (Pre-API 28)`() {
        val cookieManager = mockCookieManager()
        assertNull(prefs.getString(PREF_WEBVIEW_PROFILE_ID, null), "no WebView owner is recorded before the first run")

        ProfileManager.create(context)

        verify(exactly = 0) { cookieManager.removeAllCookies(any()) }
        verify(exactly = 0) { CookieManager.getInstance() }
        assertEquals(ProfileId.DEFAULT.value, prefs.getString(PREF_WEBVIEW_PROFILE_ID, null))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `Legacy device does not clear cookies for the same profile repeatedly (Pre-API 28)`() {
        val cookieManager = mockCookieManager()
        prefs.edit(commit = true) { putString(PREF_LAST_ACTIVE_PROFILE_ID, "p_bob") }

        // the first launch clears the cookies as p_bob takes ownership of the WebView data
        ProfileManager.create(context)
        // the second launch does not clear them again as p_bob already owns the data
        ProfileManager.create(context)

        verify(exactly = 1) { cookieManager.removeAllCookies(any()) }
        verify(exactly = 1) { cookieManager.flush() }
        assertEquals("p_bob", prefs.getString(PREF_WEBVIEW_PROFILE_ID, null))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `Legacy device clears cookies once the active profile changed (Pre-API 28)`() {
        val cookieManager = mockCookieManager()

        ProfileManager.create(context)
        prefs.edit(commit = true) { putString(PREF_LAST_ACTIVE_PROFILE_ID, "p_bob") }
        ProfileManager.create(context)

        verify(exactly = 1) { cookieManager.removeAllCookies(any()) }
        assertEquals("p_bob", prefs.getString(PREF_WEBVIEW_PROFILE_ID, null))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `Legacy device clears cookies with no recorded owner once another profile exists (Pre-API 28)`() {
        val cookieManager = mockCookieManager()
        val manager = ProfileManager.create(context)
        manager.createNewProfile(ProfileName.fromTrustedSource("Bob"))
        prefs.edit(commit = true) { remove(PREF_WEBVIEW_PROFILE_ID) }

        ProfileManager.create(context)

        verify(exactly = 1) { cookieManager.removeAllCookies(any()) }
        assertEquals(ProfileId.DEFAULT.value, prefs.getString(PREF_WEBVIEW_PROFILE_ID, null))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `Legacy device clears cookies again if the removal never completed (Pre-API 28)`() {
        val cookieManager = mockCookieManager(completesRemoval = false)
        prefs.edit(commit = true) { putString(PREF_LAST_ACTIVE_PROFILE_ID, "p_bob") }

        ProfileManager.create(context)
        ProfileManager.create(context)

        verify(exactly = 2) { cookieManager.removeAllCookies(any()) }
        assertNull(prefs.getString(PREF_WEBVIEW_PROFILE_ID, null))
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.P)
    fun `a profile that fails to load leaves the WebView directory alone (API 28+)`() {
        mockkStatic(WebView::class)
        every { WebView.setDataDirectorySuffix(any()) } just runs
        val unloadable = makeActiveProfileUnloadable()

        assertNull(ProfileManager.createOrNull(context))

        verify(exactly = 0) { WebView.setDataDirectorySuffix(unloadable.value) }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `a profile that fails to load keeps the WebView cookies (Pre-API 28)`() {
        val cookieManager = mockCookieManager()
        makeActiveProfileUnloadable()

        assertNull(ProfileManager.createOrNull(context))

        verify(exactly = 0) { cookieManager.removeAllCookies(any()) }
        assertEquals(ProfileId.DEFAULT.value, prefs.getString(PREF_WEBVIEW_PROFILE_ID, null))
    }

    @Test
    fun `getAllProfiles ignores bookkeeping keys whose value parses as profile JSON`() {
        val manager = ProfileManager.create(context)
        val metadataJson =
            ProfileManager
                .ProfileMetadata(displayName = ProfileName.fromTrustedSource("Not a profile"))
                .toJson()

        prefs.edit(commit = true) { putString(PREF_WEBVIEW_PROFILE_ID, metadataJson) }

        assertFalse(
            "Bookkeeping keys must never surface as profiles",
            manager.getAllProfiles().containsKey(ProfileId(PREF_WEBVIEW_PROFILE_ID)),
        )
    }

    @Test
    fun `switching the profile persists synchronously so it survives the process kill`() {
        val registry = RecordingPreferences(context.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE))
        val wrapper =
            object : ContextWrapper(context) {
                override fun getApplicationContext(): Context? = null

                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ): SharedPreferences = if (name == PROFILE_REGISTRY_FILENAME) registry else super.getSharedPreferences(name, mode)
            }
        val manager = ProfileManager.create(wrapper)
        registry.editors.clear()

        with(ProfileManager.ProfileSwitchContext) { manager.switchActiveProfile(ProfileId("p_other")) }

        assertEquals("apply() is async and is lost when the process is killed", 1, registry.editors.sumOf { it.commits })
        assertEquals(0, registry.editors.sumOf { it.applies })
    }

    @Test
    fun `switching sets up the new profile before saving it`() {
        val manager = ProfileManager.create(context)
        val work = manager.createNewProfile(ProfileName.fromTrustedSource("Work"))

        with(ProfileManager.ProfileSwitchContext) { manager.switchActiveProfile(work) }

        assertTrue("the profile folder must exist before the restart", File(appDataRoot, work.value).isDirectory)
        val deckPath = profilePrefs(work).getString(PREF_COLLECTION_PATH, null)
        assertEquals(File(context.getExternalFilesDir(null), work.value).absolutePath, deckPath)
        assertTrue("the collection folder must exist before the restart", File(deckPath!!).isDirectory)
    }

    @Test
    fun `a profile that cannot be set up is not switched to`() {
        val manager = ProfileManager.create(context)
        val work = manager.createNewProfile(ProfileName.fromTrustedSource("Work"))
        File(appDataRoot, work.value).writeText("a file where the profile folder belongs")

        val switched = with(ProfileManager.ProfileSwitchContext) { manager.switchActiveProfile(work) }

        assertFalse(switched)
        assertEquals(ProfileId.DEFAULT.value, prefs.getString(PREF_LAST_ACTIVE_PROFILE_ID, null))
    }

    @Test
    fun `switching keeps a collection folder the profile already chose`() {
        val manager = ProfileManager.create(context)
        val work = manager.createNewProfile(ProfileName.fromTrustedSource("Work"))
        val chosen = File(context.getExternalFilesDir(null), "chosen-by-work").apply { mkdirs() }
        writeProfileCollectionPath(work, chosen)

        with(ProfileManager.ProfileSwitchContext) { manager.switchActiveProfile(work) }

        assertEquals(chosen.absolutePath, profilePrefs(work).getString(PREF_COLLECTION_PATH, null))
    }

    @Test
    fun `switching back to Default needs no setup`() {
        val work = ProfileManager.create(context).createNewProfile(ProfileName.fromTrustedSource("Work"))
        prefs.edit(commit = true) { putString(PREF_LAST_ACTIVE_PROFILE_ID, work.value) }
        val manager = ProfileManager.create(context)

        val switched = with(ProfileManager.ProfileSwitchContext) { manager.switchActiveProfile(ProfileId.DEFAULT) }

        assertTrue(switched)
        assertEquals(ProfileId.DEFAULT.value, prefs.getString(PREF_LAST_ACTIVE_PROFILE_ID, null))
    }

    @Test
    @Config(minSdk = Build.VERSION_CODES.P)
    fun `setting up the new profile leaves this process's WebView alone (API 28+)`() {
        mockkStatic(WebView::class)
        every { WebView.setDataDirectorySuffix(any()) } just runs
        val manager = ProfileManager.create(context)
        val work = manager.createNewProfile(ProfileName.fromTrustedSource("Work"))

        with(ProfileManager.ProfileSwitchContext) { manager.switchActiveProfile(work) }

        verify(exactly = 0) { WebView.setDataDirectorySuffix(any()) }
        assertEquals(ProfileId.DEFAULT, manager.activeProfileContext.profileId)
    }

    @Test
    fun `a switch that cannot be saved keeps the current profile`() {
        val registry = DiskFullPreferences(context.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE))
        val manager = ProfileManager.create(contextWithRegistry(registry))
        val work = manager.createNewProfile(ProfileName.fromTrustedSource("Work"))
        registry.isDiskFull = true

        val switched = with(ProfileManager.ProfileSwitchContext) { manager.switchActiveProfile(work) }

        assertFalse(switched)
        assertEquals(
            "a failed commit keeps the new value in memory, so the next write would save the switch after all",
            ProfileId.DEFAULT.value,
            registry.getString(PREF_LAST_ACTIVE_PROFILE_ID, null),
        )
    }

    @Test
    fun `restoring the running profile saves it again if the save fails`() {
        val diskFull = DiskFullPreferences(context.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE))
        val registry = RecordingPreferences(diskFull)
        val manager = ProfileManager.create(contextWithRegistry(registry))
        val work = manager.createNewProfile(ProfileName.fromTrustedSource("Work"))
        with(ProfileManager.ProfileSwitchContext) { manager.switchActiveProfile(work) }
        diskFull.isDiskFull = true
        registry.editors.clear()

        manager.restoreRunningProfile()

        assertEquals(ProfileId.DEFAULT.value, registry.getString(PREF_LAST_ACTIVE_PROFILE_ID, null))
        assertEquals("the failed commit left the switch target on disk", 1, registry.editors.sumOf { it.applies })
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
    fun `a new profile ID that matches an existing profile is generated again`() {
        val manager = ProfileManager.create(context)
        mockkStatic(UUID::class)
        every { UUID.randomUUID() } returnsMany
            listOf(
                UUID.fromString("aaaaaaaa-0000-4000-8000-000000000000"),
                UUID.fromString("aaaaaaaa-1111-4111-8111-111111111111"),
                UUID.fromString("bbbbbbbb-0000-4000-8000-000000000000"),
            )

        val first = manager.createNewProfile(ProfileName.fromTrustedSource("Work"))
        val second = manager.createNewProfile(ProfileName.fromTrustedSource("Personal"))

        assertEquals(ProfileId("p_aaaaaaaa"), first)
        assertEquals(ProfileId("p_bbbbbbbb"), second)
    }

    @Test
    fun `createNewProfile rejects a name already in use, ignoring case`() {
        val manager = ProfileManager.create(context)
        manager.createNewProfile(ProfileName.fromTrustedSource("Work"))

        assertThrows(IllegalArgumentException::class.java) {
            manager.createNewProfile(ProfileName.fromTrustedSource("work"))
        }
        assertEquals(2, manager.getAllProfiles().size)
    }

    @Test
    fun `createNewProfile does not log the profile name`() {
        val manager = ProfileManager.create(context)

        val logs = captureLogs { manager.createNewProfile(ProfileName.fromTrustedSource("Alice")) }

        assertTrue("Expected createNewProfile to log", logs.isNotEmpty())
        assertFalse("Profile name was logged: $logs", logs.any { it.contains("Alice") })
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
    fun `loading a non-default profile keeps deckPath in app storage when Default uses legacy storage`() {
        ShadowEnvironment.setIsExternalStorageLegacy(true)
        context.sharedPrefs().edit(commit = true) {
            putString(PREF_COLLECTION_PATH, File(Environment.getExternalStorageDirectory(), "AnkiDroid").absolutePath)
        }
        val manager = ProfileManager.create(context)
        val ashishId = manager.createNewProfile(ProfileName.fromTrustedSource("Ashish"))
        with(ProfileManager.ProfileSwitchContext) { manager.switchActiveProfile(ashishId) }

        val reloaded = ProfileManager.create(context)
        val deckPath = reloaded.activeProfileContext.sharedPrefs().getString(PREF_COLLECTION_PATH, null)

        assertEquals(File(context.getExternalFilesDir(null), ashishId.value).absolutePath, deckPath)
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
    fun `renameProfile rejects a name used by another profile, ignoring case`() {
        val manager = ProfileManager.create(context)
        manager.createNewProfile(ProfileName.fromTrustedSource("Work"))
        val home = manager.createNewProfile(ProfileName.fromTrustedSource("Home"))

        assertThrows(IllegalArgumentException::class.java) {
            manager.renameProfile(home, ProfileName.fromTrustedSource("work"))
        }
        assertEquals("Home", manager.getAllProfiles()[home]?.displayName?.value)
    }

    @Test
    fun `renameProfile can change the case of the profile's own name`() {
        val manager = ProfileManager.create(context)
        val work = manager.createNewProfile(ProfileName.fromTrustedSource("work"))

        manager.renameProfile(work, ProfileName.fromTrustedSource("Work"))

        assertEquals("Work", manager.getAllProfiles()[work]?.displayName?.value)
    }

    @Test
    fun `reloading an existing profile does not overwrite a pre-existing deckPath`() {
        val manager = ProfileManager.create(context)
        val newId = manager.createNewProfile(ProfileName.fromTrustedSource("Work"))
        with(ProfileManager.ProfileSwitchContext) { manager.switchActiveProfile(newId) }

        // First load materializes deckPath. Then the user "relocates" their collection.
        val firstLoad = ProfileManager.create(context)
        val userChosenPath =
            File(context.filesDir, "user_relocated").apply { mkdirs() }.absolutePath
        firstLoad.activeProfileContext.sharedPrefs().edit(commit = true) {
            putString(PREF_COLLECTION_PATH, userChosenPath)
        }

        // Simulate app restart - ProfileManager.create runs again.
        val reloaded = ProfileManager.create(context)
        val deckPath =
            reloaded.activeProfileContext.sharedPrefs().getString(PREF_COLLECTION_PATH, null)

        assertEquals(
            "User-relocated deckPath must not be overwritten on reload",
            userChosenPath,
            deckPath,
        )
    }

    @Test
    fun `deleteProfile removes profile from registry`() {
        val manager = ProfileManager.create(context)
        val profileId = manager.createNewProfile(ProfileName.fromTrustedSource("Temporary"))

        manager.deleteProfile(profileId)

        val allProfiles = manager.getAllProfiles()
        assertFalse(allProfiles.containsKey(profileId))
    }

    @Test
    fun `deleteProfile does not remove other profiles`() {
        val manager = ProfileManager.create(context)
        val keep = manager.createNewProfile(ProfileName.fromTrustedSource("Keep"))
        val remove = manager.createNewProfile(ProfileName.fromTrustedSource("Remove"))

        manager.deleteProfile(remove)

        val allProfiles = manager.getAllProfiles()
        assertEquals(2, allProfiles.size)
        assertTrue(allProfiles.containsKey(ProfileId.DEFAULT))
        assertTrue(allProfiles.containsKey(keep))
    }

    @Test
    fun `deleteProfile allows deleting default when not active`() {
        val manager = ProfileManager.create(context)
        val other = manager.createNewProfile(ProfileName.fromTrustedSource("Other"))

        prefs.edit(commit = true) {
            putString(PREF_LAST_ACTIVE_PROFILE_ID, other.value)
        }

        val freshManager = ProfileManager.create(context)
        freshManager.deleteProfile(ProfileId.DEFAULT)

        assertFalse(freshManager.getAllProfiles().containsKey(ProfileId.DEFAULT))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `deleteProfile throws when deleting active profile`() {
        val manager = ProfileManager.create(context)
        val profileId = manager.createNewProfile(ProfileName.fromTrustedSource("Active"))

        prefs.edit(commit = true) {
            putString(PREF_LAST_ACTIVE_PROFILE_ID, profileId.value)
        }

        val freshManager = ProfileManager.create(context)
        freshManager.deleteProfile(profileId)
    }

    @Test
    fun `deleteProfile for Default profile wipes legacy root folders but preserves Alice and Bob`() {
        val manager = ProfileManager.create(context)

        val aliceId = manager.createNewProfile(ProfileName.fromTrustedSource("Alice"))
        val aliceDir = File(appDataRoot, aliceId.value).apply { mkdirs() }
        val alicePref =
            File(appDataRoot, "shared_prefs/profile_${aliceId.value}.xml").apply {
                parentFile?.mkdirs()
                createNewFile()
            }

        val defaultFile =
            File(context.filesDir, "legacy_default.txt").apply {
                parentFile?.mkdirs()
                writeText("Old data")
            }
        val defaultWebview = File(appDataRoot, "app_webview").apply { mkdirs() }
        val defaultPref =
            File(appDataRoot, "shared_prefs/com.ichi2.anki_preferences.xml").apply {
                createNewFile()
            }

        with(ProfileManager.ProfileSwitchContext) { manager.switchActiveProfile(aliceId) }
        val managerWithAlice = ProfileManager.create(context)

        managerWithAlice.deleteProfile(ProfileId.DEFAULT)

        //  Default data should be gone
        assertFalse("Default's filesDir content should be gone", defaultFile.exists())
        assertFalse("Default's webview folder should be gone", defaultWebview.exists())
        assertFalse("Default's specific SharedPreferences should be gone", defaultPref.exists())

        // Alice and Global Registry remains
        assertTrue("Alice's directory must not be touched", aliceDir.exists())
        assertTrue("Alice's SharedPreferences must not be touched", alicePref.exists())
        assertTrue(
            "The Profile Registry file itself must not be deleted",
            File(appDataRoot, "shared_prefs/$PROFILE_REGISTRY_FILENAME.xml").exists(),
        )

        assertFalse(
            "Registry should no longer contain Default",
            managerWithAlice.getAllProfiles().containsKey(ProfileId.DEFAULT),
        )
    }

    @Test
    fun `deleteProfile with user-relocated collection only removes known AnkiDroid artifacts`() {
        val manager = ProfileManager.create(context)
        val profileId = manager.createNewProfile(ProfileName.fromTrustedSource("Relocated"))

        // Simulate a user who has moved their collection to an arbitrary directory
        // (e.g. /Pictures/MyAnki/) that also contains unrelated personal files.
        val userDir = File(appDataRoot, "user_pictures_like").apply { mkdirs() }
        writeProfileCollectionPath(profileId, userDir)

        val colDb = File(userDir, "collection.anki2").apply { createNewFile() }
        val noMedia = File(userDir, ".nomedia").apply { createNewFile() }
        val mediaFolder =
            File(userDir, "collection.media").apply {
                mkdirs()
                File(this, "img_123.jpg").createNewFile()
            }
        val backupFolder =
            File(userDir, "backup").apply {
                mkdirs()
                File(this, "collection-2026-04-18-10-30.colpkg").createNewFile()
            }

        // Unrelated user files
        val holidayPhoto = File(userDir, "holiday.jpg").apply { writeText("pixel data") }
        val unrelatedFolder =
            File(userDir, "family").apply {
                mkdirs()
                File(this, "photo.png").createNewFile()
            }

        manager.deleteProfile(profileId)

        assertFalse("collection.anki2 should be deleted", colDb.exists())
        assertFalse(".nomedia should be deleted", noMedia.exists())
        assertFalse("collection.media folder should be deleted", mediaFolder.exists())
        assertFalse("backup folder should be deleted", backupFolder.exists())

        assertTrue("Unrelated holiday.jpg MUST survive", holidayPhoto.exists())
        assertTrue("Unrelated family/ MUST survive", unrelatedFolder.exists())
        assertTrue(
            "Unrelated file inside family/ MUST survive",
            File(unrelatedFolder, "photo.png").exists(),
        )
        assertTrue(
            "User's own directory must not be removed because it still has their data",
            userDir.exists(),
        )
    }

    @Test
    fun `deleteProfile removes the collection directory only when it becomes empty`() {
        val manager = ProfileManager.create(context)
        val profileId = manager.createNewProfile(ProfileName.fromTrustedSource("Clean"))

        val collectionDir = File(appDataRoot, "standalone_collection").apply { mkdirs() }
        writeProfileCollectionPath(profileId, collectionDir)

        File(collectionDir, "collection.anki2").createNewFile()
        File(collectionDir, ".nomedia").createNewFile()

        manager.deleteProfile(profileId)

        assertFalse(
            "Collection dir should be removed once it has no remaining contents",
            collectionDir.exists(),
        )
    }

    @Test
    fun `deleteProfile recursively removes known subfolders inside a relocated collection`() {
        val manager = ProfileManager.create(context)
        val profileId = manager.createNewProfile(ProfileName.fromTrustedSource("MediaAndBackups"))

        val collectionDir = File(appDataRoot, "custom_col").apply { mkdirs() }
        writeProfileCollectionPath(profileId, collectionDir)

        val mediaNested = File(File(collectionDir, "collection.media"), "sub/deep").apply { mkdirs() }
        val deepMediaFile = File(mediaNested, "pic.jpg").apply { createNewFile() }
        val backupFile =
            File(File(collectionDir, "backup"), "col.colpkg").apply {
                parentFile?.mkdirs()
                createNewFile()
            }

        manager.deleteProfile(profileId)

        assertFalse("Nested media file should be deleted recursively", deepMediaFile.exists())
        assertFalse("Nested media dir should be deleted recursively", mediaNested.exists())
        assertFalse("Backup file should be deleted recursively", backupFile.exists())
    }

    @Test
    fun `deleteProfile recursively removes media-trash folder`() {
        val manager = ProfileManager.create(context)
        val profileId = manager.createNewProfile(ProfileName.fromTrustedSource("WithTrash"))

        val collectionDir = File(appDataRoot, "trash_col").apply { mkdirs() }
        writeProfileCollectionPath(profileId, collectionDir)

        val trashFile =
            File(File(collectionDir, "media.trash"), "deleted_img.jpg").apply {
                parentFile?.mkdirs()
                createNewFile()
            }

        manager.deleteProfile(profileId)

        assertFalse("media.trash contents should be deleted", trashFile.exists())
        assertFalse("media.trash folder should be deleted", trashFile.parentFile!!.exists())
    }

    /** Stubs [CookieManager.getInstance]. [completesRemoval] fires the cookie removal callback. */
    private class RecordingEditor(
        private val delegate: SharedPreferences.Editor,
    ) : SharedPreferences.Editor by delegate {
        var commits = 0
        var applies = 0

        override fun commit(): Boolean {
            commits++
            return delegate.commit()
        }

        override fun apply() {
            applies++
            delegate.apply()
        }
    }

    private class RecordingPreferences(
        private val delegate: SharedPreferences,
    ) : SharedPreferences by delegate {
        val editors = mutableListOf<RecordingEditor>()

        override fun edit(): SharedPreferences.Editor = RecordingEditor(delegate.edit()).also { editors += it }
    }

    private class DiskFullPreferences(
        private val delegate: SharedPreferences,
    ) : SharedPreferences by delegate {
        var isDiskFull = false

        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun commit(): Boolean {
                    if (!isDiskFull) return editor.commit()
                    editor.apply()
                    return false
                }
            }
        }
    }

    private fun profilePrefs(profileId: ProfileId) =
        context.getSharedPreferences("profile_${profileId.value}_${context.packageName}_preferences", Context.MODE_PRIVATE)

    private fun contextWithRegistry(registry: SharedPreferences) =
        object : ContextWrapper(context) {
            override fun getApplicationContext(): Context? = null

            override fun getSharedPreferences(
                name: String,
                mode: Int,
            ): SharedPreferences = if (name == PROFILE_REGISTRY_FILENAME) registry else super.getSharedPreferences(name, mode)
        }

    private fun makeActiveProfileUnloadable(): ProfileId {
        val profileId = ProfileManager.create(context).createNewProfile(ProfileName.fromTrustedSource("Work"))
        File(appDataRoot, profileId.value).writeText("a file where the profile folder belongs")
        prefs.edit(commit = true) { putString(PREF_LAST_ACTIVE_PROFILE_ID, profileId.value) }
        return profileId
    }

    private fun mockCookieManager(completesRemoval: Boolean = true): CookieManager {
        mockkStatic(CookieManager::class)
        val cookieManager = mockk<CookieManager>(relaxed = true)
        every { CookieManager.getInstance() } returns cookieManager
        if (completesRemoval) {
            every { cookieManager.removeAllCookies(any()) } answers {
                firstArg<ValueCallback<Boolean>>().onReceiveValue(true)
            }
        }
        return cookieManager
    }

    private fun writeProfileCollectionPath(
        profileId: ProfileId,
        dir: File,
    ) {
        val defaultPrefsName = "${context.packageName}_preferences"
        val prefsName = "profile_${profileId.value}_$defaultPrefsName"
        context
            .getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit(commit = true) { putString(PREF_COLLECTION_PATH, dir.absolutePath) }
    }

    private fun captureLogs(block: () -> Unit): List<String> {
        val messages = mutableListOf<String>()
        val tree =
            object : Timber.Tree() {
                override fun log(
                    priority: Int,
                    tag: String?,
                    message: String,
                    t: Throwable?,
                ) {
                    messages += message
                }
            }
        Timber.plant(tree)
        try {
            block()
        } finally {
            Timber.uproot(tree)
        }
        return messages
    }
}
