// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.KeyEvent
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.ext.schedulerVersion
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.testutils.BackupManagerTestUtilities
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckPickerSchedulerUpgradeTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var qualifiers: String = ""

    @Before
    fun prepareCollection() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        setIntroductionSlidesShown(true)
        addBasicNote()
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        InitialActivity.setUpgradedToLatestVersion(targetContext.sharedPrefs())
        getPreferences().edit { putBoolean("backupPromptDisabled", true) }
    }

    @After
    fun resetBackupSpace() = BackupManagerTestUtilities.reset()

    @Test
    fun `required prompt survives repeated recreation and collection refresh`() {
        val controller = launchWithPrompt()
        val viewModel = controller.get().viewModel
        val original = upgradeDialog(controller.get())

        repeat(2) {
            recreate(controller)
            assertSame(viewModel, controller.get().viewModel)
            refresh(controller.get())
            val restored = upgradeDialog(controller.get())
            assertNotSame(original, restored)
            assertEquals(original.title, restored.title)
            assertEquals(original.message, restored.message)
            assertEquals(1L, col.config.schedulerVersion)
        }
    }

    @Test
    fun `confirming the restored prompt upgrades once`() {
        val controller = launchWithPrompt()
        recreate(controller)
        val dialog = upgradeDialog(controller.get())
        ShadowToast.reset()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooperUntil { col.config.schedulerVersion == 2L }
        advanceRobolectricLooper()

        assertFalse(dialog.isShowing)
        assertEquals(1, ShadowToast.shownToastCount())
        assertEquals(TR.schedulingUpdateDone(), ShadowToast.getTextOfLatestToast())
        recreate(controller)
        refresh(controller.get())
        assertNoUpgradePrompt()
        assertEquals(1, ShadowToast.shownToastCount())
    }

    @Test
    fun `Cancel on the restored prompt uses the current activity`() = cancelRestoredPrompt(back = false)

    @Test
    fun `Back on the restored prompt uses the current activity`() = cancelRestoredPrompt(back = true)

    private fun cancelRestoredPrompt(back: Boolean) {
        val controller = launchWithPrompt()
        recreate(controller)
        val activity = controller.get()
        var cancellations = 0
        activity.onBackPressedDispatcher.addCallback(
            activity,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    cancellations++
                }
            },
        )
        val dialog = upgradeDialog(activity)

        if (back) {
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        } else {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        }
        advanceRobolectricLooper()

        assertEquals(1, cancellations)
        assertEquals(1L, col.config.schedulerVersion)
        assertFalse(dialog.isShowing)
        recreate(controller)
        refresh(controller.get())
        assertNoUpgradePrompt()
    }

    private fun launchWithPrompt(): ActivityController<DeckPicker> {
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        col.config.set("schedVer", 1L)
        col.modSchema(check = false)
        refresh(controller.get())
        upgradeDialog(controller.get())
        return controller
    }

    private fun refresh(activity: DeckPicker) {
        // Not runBlocking: the reload emits to flows DeckPicker collects on the main thread
        val reload = activity.viewModel.reloadDeckCounts()
        advanceRobolectricLooperUntil { reload.isCompleted }
    }

    private fun recreate(controller: ActivityController<DeckPicker>) {
        val original = controller.get()
        controller.recreate()
        controller.get().viewModel.awaitStartup()
        advanceRobolectricLooper()
        assertNotSame(original, controller.get())
    }

    private fun upgradeDialog(activity: DeckPicker): AlertDialog {
        val expectedTitle = activity.getString(CommonString.scheduler_upgrade_required_dialog_title)
        // Wait for the upgrade prompt itself: another dialog (e.g. a startup error) must time out
        // with the dialogs that were showing, rather than fail the title check
        val showingTitles = { showingAlertDialogs().map { it.title } }
        advanceRobolectricLooperUntil(
            lazyMessage = { "'$expectedTitle' was not shown. Showing dialogs: ${showingTitles()}" },
        ) { expectedTitle in showingTitles() }
        val dialogs = showingAlertDialogs()
        assertEquals(1, dialogs.size, "Showing dialogs: ${showingTitles()}")
        return dialogs.single().also {
            assertTrue(it.isShowing)
            assertSame(activity, it.context.findAnkiActivity())
        }
    }

    private fun showingAlertDialogs() = ShadowDialog.getShownDialogs().filterIsInstance<AlertDialog>().filter { it.isShowing }

    private fun assertNoUpgradePrompt() {
        assertFalse(ShadowDialog.getShownDialogs().any { it.isShowing })
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        @JvmStatic
        fun configurations() = listOf("normal", "xlarge")
    }
}
