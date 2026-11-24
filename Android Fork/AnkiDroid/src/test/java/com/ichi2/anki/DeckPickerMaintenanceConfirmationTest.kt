// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.utils.message
import com.ichi2.anki.dialogs.utils.title
import com.ichi2.anki.mediacheck.MediaCheckFragment
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.ShadowStatFs
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class DeckPickerMaintenanceConfirmationTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter(0)
    @JvmField
    var qualifiers: String = ""

    @ParameterizedRobolectricTestRunner.Parameter(1)
    @JvmField
    var mediaCheck: Boolean = false

    private val databaseChecks = AtomicInteger()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Before
    fun prepareCollection() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride {
            object : Backend() {
                override fun checkDatabase(): List<String> {
                    databaseChecks.incrementAndGet()
                    return listOf("Maintenance check finished")
                }
            }
        }
        setIntroductionSlidesShown(true)
        addBasicNote()
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        InitialActivity.setUpgradedToLatestVersion(targetContext.sharedPrefs())
        getPreferences().edit { putBoolean("backupPromptDisabled", true) }
    }

    @After
    fun resetOverrides() {
        BackendFactory.setOverride(null)
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `confirmation survives recreation and continues once`() {
        val controller = launch()
        val dialog = openAndRecreate(controller)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()

        if (mediaCheck) {
            val intent = assertNotNull(shadowOf(controller.get()).nextStartedActivity)
            val expected = MediaCheckFragment.getIntent(controller.get())
            assertEquals(expected.component, intent.component)
            assertEquals(MediaCheckFragment::class.java.name, intent.getStringExtra(SingleFragmentActivity.EXTRA_FRAGMENT_NAME))
        } else {
            advanceRobolectricLooperUntil { databaseChecks.get() == 1 && latestDialog().message == "Maintenance check finished" }
            assertSame(controller.get(), latestDialog().context.findAnkiActivity())
            latestDialog().dismiss()
            advanceRobolectricLooper()
        }
        assertFalse(dialog.isShowing)
        assertNull(shadowOf(controller.get()).nextStartedActivity)

        recreate(controller)

        assertEquals(if (mediaCheck) 0 else 1, databaseChecks.get())
        assertNull(shadowOf(controller.get()).nextStartedActivity)
        assertFalse(ShadowDialog.getShownDialogs().any { it.isShowing })
    }

    @Test
    fun `cancelling the restored confirmation starts no check`() {
        val controller = launch()
        val dialog = openAndRecreate(controller)

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        advanceRobolectricLooper()
        assertFalse(dialog.isShowing)

        recreate(controller)

        assertNoCheckStarted(controller.get())
        assertFalse(ShadowDialog.getShownDialogs().any { it.isShowing })
    }

    @Test
    fun `cancelling without a button starts no check`() {
        val controller = launch()
        val dialog = openAndRecreate(controller)

        // Dialog.cancel() is the cancellation path used by Back and outside touches.
        dialog.cancel()
        advanceRobolectricLooper()
        assertFalse(dialog.isShowing)

        recreate(controller)

        assertNoCheckStarted(controller.get())
        assertFalse(ShadowDialog.getShownDialogs().any { it.isShowing })
    }

    @Test
    fun `recreation without a pending confirmation starts no check`() {
        val controller = launch()

        recreate(controller)

        assertNoCheckStarted(controller.get())
        assertFalse(ShadowDialog.getShownDialogs().any { it.isShowing })
    }

    @Test
    fun `database check without a storage warning starts immediately`() {
        assumeFalse(mediaCheck)
        val activity = launch().get()
        ShadowStatFs.markAsNonEmpty(CollectionHelper.getCurrentAnkiDroidDirectory(activity))
        assertFalse(CollectionIntegrityStorageCheck.createInstance(activity).shouldWarnOnIntegrityCheck())

        activity.integrityCheck()

        advanceRobolectricLooperUntil { databaseChecks.get() == 1 && latestDialog().message == "Maintenance check finished" }
    }

    private fun launch(): ActivityController<DeckPicker> =
        startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())

    private fun openAndRecreate(controller: ActivityController<DeckPicker>): AlertDialog {
        val activity = controller.get()
        if (mediaCheck) {
            activity.mediaCheck()
        } else {
            val directory = CollectionHelper.getCurrentAnkiDroidDirectory(activity)
            ShadowStatFs.registerStats(directory, 100, 0, 0)
            try {
                assertTrue(CollectionIntegrityStorageCheck.createInstance(activity).shouldWarnOnIntegrityCheck())
                activity.integrityCheck()
            } finally {
                ShadowStatFs.markAsNonEmpty(directory)
            }
        }
        advanceRobolectricLooper()
        val original = latestDialog()
        assertTrue(original.isShowing)
        assertSame(activity, original.context.findAnkiActivity())
        val title = original.title
        val message = original.message
        assertNoCheckStarted(activity)

        repeat(2) {
            recreate(controller)
            val restored = latestDialog()
            assertNotSame(original, restored)
            assertSame(controller.get(), restored.context.findAnkiActivity())
            assertTrue(restored.isShowing)
            assertEquals(title, restored.title)
            assertEquals(message, restored.message)
            assertEquals(1, ShadowDialog.getShownDialogs().count { it.isShowing })
            assertNoCheckStarted(controller.get())
        }
        return latestDialog()
    }

    private fun recreate(controller: ActivityController<DeckPicker>) {
        val original = controller.get()
        controller.recreate()
        controller.get().viewModel.awaitStartup()
        advanceRobolectricLooper()
        assertNotSame(original, controller.get())
    }

    private fun assertNoCheckStarted(activity: DeckPicker) {
        assertEquals(0, databaseChecks.get())
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    private fun latestDialog(): AlertDialog = assertNotNull(ShadowDialog.getLatestDialog() as? AlertDialog)

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}, mediaCheck={1}")
        @JvmStatic
        fun configurations(): List<Array<Any>> =
            listOf("normal", "xlarge").flatMap { qualifiers ->
                listOf(true, false).map { mediaCheck -> arrayOf(qualifiers, mediaCheck) }
            }
    }
}
