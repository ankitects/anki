// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.InitialActivity.StartupFailure
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PREF_LAST_ACTIVE_PROFILE_ID
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PROFILE_REGISTRY_FILENAME
import com.ichi2.testutils.neutralButton
import io.mockk.verify
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerProfileRecoveryTest : RobolectricTest() {
    private val app: AnkiDroidApp
        get() = targetContext as AnkiDroidApp

    private var originalProfileManager: ProfileManager? = null

    private val defaultCollectionError = StartupFailure.DBError(IllegalStateException("Default collection could not be opened"))

    @Before
    fun rememberProfileManager() {
        originalProfileManager = app.profileManager
    }

    @After
    fun resetProfileManager() {
        app.profileManager = originalProfileManager
        ProfileManager.resetForTesting()
    }

    @Test
    @SuppressLint("CheckResult")
    fun `profile recovery is available when Default startup fails`() {
        failToOpenWork()
        // Exercise the fatal startup branch, rather than the backup-space warning
        // which is only reached after the fallback collection opens successfully.
        withStartupFailure(StartupFailure.DiskFull) {
            deckPicker {
                verify { InitialActivity.getStartupFailureType(any<SharedPreferences>(), any<() -> Boolean>()) }
                val recovery = assertNotNull(profileLoadFailureDialog, "Failed Default startup must still offer profile recovery")
                recovery.neutralButton.performClick()
                advanceRobolectricLooper()

                assertEquals(
                    ProfileId.DEFAULT.value,
                    app.baseContext
                        .getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
                        .getString(PREF_LAST_ACTIVE_PROFILE_ID, null),
                )
            }
        }
    }

    @Test
    fun `restored profile failure is discarded when the selected profile now opens`() {
        val work = failToOpenWork()
        val controller = startDeckPicker()
        assertNotNull(controller.get().profileLoadFailureDialog)
        val savedState = controller.saveAndDestroy()

        assertTrue(File(app.baseContext.filesDir.parentFile, work.value).delete())
        // At Application.attachBaseContext the application context is unavailable.
        // Keep the new manager on the framework base, as in a real process restart.
        val base =
            object : ContextWrapper(app.baseContext) {
                override fun getApplicationContext(): Context? = null
            }
        app.profileManager = assertNotNull(ProfileManager.createOrNull(base))
        assertEquals(work, app.profileManager?.activeProfileContext?.profileId)
        assertNull(ProfileManager.attachError)
        // Work has never opened before in this fixture, so skip its onboarding.
        setIntroductionSlidesShown(true)

        val restored = restoreDeckPicker(savedState)

        assertNull(
            restored.profileLoadFailureDialog,
            "Work opened successfully; a restored warning must not claim Default is active or offer a stale recovery action",
        )
    }

    @Test
    fun `profile recovery remains above fatal startup error after background and foreground`() {
        failToOpenWork()
        withStartupFailure(defaultCollectionError) {
            val controller = startDeckPicker()
            val recovery = assertNotNull(controller.get().profileLoadFailureDialog)
            assertTopDialog(recovery.requireDialog(), "Recovery is initially on top")

            controller.leaveAndReturn()

            assertTopDialog(
                recovery.requireDialog(),
                "Returning to the app must keep profile recovery reachable above a noncancelable error",
            )
        }
    }

    @Test
    fun `profile recovery remains above fatal startup error after recreation`() {
        failToOpenWork()
        withStartupFailure(defaultCollectionError) {
            val controller = startDeckPicker()
            val recovery = assertNotNull(controller.get().profileLoadFailureDialog)
            assertTopDialog(recovery.requireDialog(), "Recovery is initially on top")

            val restored = assertNotNull(controller.recreateDeckPicker().profileLoadFailureDialog)

            assertTopDialog(restored.requireDialog(), "Restored recovery must stay above the noncancelable startup error")
        }
    }

    @Test
    fun `recovery does not silently dismiss when its registry cannot be written`() {
        val work = failToOpenWork()
        val recovery = assertNotNull(startDeckPicker().get().profileLoadFailureDialog)
        val prefsDirectory = File(targetContext.filesDir.parentFile, "shared_prefs")
        val registryFile = File(prefsDirectory, "$PROFILE_REGISTRY_FILENAME.xml")
        assertTrue(registryFile.isFile)
        val diskBefore = registryFile.readText()
        assertTrue(diskBefore.contains(work.value))
        assumeTrue("The filesystem must support making directories unwritable", prefsDirectory.setWritable(false, false))
        try {
            // A privileged test runner may still be able to write despite these permissions.
            assumeFalse("The registry directory must actually be unwritable", prefsDirectory.canWrite())
            val dialog = recovery.requireDialog()
            recovery.neutralButton.performClick()
            advanceRobolectricLooper()

            assertEquals(diskBefore, registryFile.readText(), "The failed commit leaves the old selection on disk")
            assertTrue(dialog.isShowing, "A failed recovery write must leave the recovery action available")
        } finally {
            assertTrue(prefsDirectory.setWritable(true, true))
        }
    }

    private fun failToOpenWork(): ProfileId = failToOpenProfile().also { app.profileManager = null }

    private fun assertTopDialog(
        expected: Dialog,
        message: String,
    ) {
        // A deck refresh can briefly show a progress dialog, which getLatestDialog()
        // still returns after dismissal. Wait for the top dialog that is actually showing.
        advanceRobolectricLooperUntil(
            lazyMessage = { "$message; expected $expected, showing dialogs: ${ShadowDialog.getShownDialogs().filter { it.isShowing }}" },
        ) {
            ShadowDialog.getShownDialogs().lastOrNull { it.isShowing } === expected
        }
    }

    private fun startDeckPicker(): ActivityController<DeckPicker> {
        setIntroductionSlidesShown(true)
        return startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).also {
            advanceRobolectricLooper()
        }
    }

    private fun ActivityController<DeckPicker>.saveAndDestroy(): Bundle =
        Bundle().also { savedState ->
            pause()
                .stop()
                .saveInstanceState(savedState)
                .destroy()
        }

    private fun restoreDeckPicker(savedState: Bundle): DeckPicker {
        val controller: ActivityController<DeckPicker> =
            Robolectric
                .buildActivity(DeckPicker::class.java, Intent())
                .create(savedState)
                .start()
                .restoreInstanceState(savedState)
                .postCreate(savedState)
                .resume()
                .visible()
        saveControllerForCleanup(controller)
        controller.get().viewModel.awaitStartup()
        advanceRobolectricLooper()
        return controller.get()
    }
}
