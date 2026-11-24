// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.analytics.AnkiDroidUsageAnalytics
import com.ichi2.anki.dialogs.DeckPickerAnalyticsOptInDialog
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.utils.getCheckBoxPrompt
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerAnalyticsDialogTest : RobolectricTest() {
    @Before
    fun prepareUpgrade() {
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        getPreferences().edit {
            putString("lastVersion", "0.1")
            putBoolean("backupPromptDisabled", true)
            remove(AnkiDroidUsageAnalytics.PREF_ANALYTICS_OPTIN)
        }
    }

    @After
    fun resetBackupSpace() = BackupManagerTestUtilities.reset()

    @Test
    fun `startup preserves the restored analytics dialog and its selection`() {
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        val original = controller.get().analyticsDialog()
        original.getCheckBoxPrompt().isChecked = true

        repeat(2) {
            // Preference upgrades can recreate DeckPicker before lastVersion is updated.
            getPreferences().edit { putString("lastVersion", "0.1") }
            controller.recreate()
            controller.get().viewModel.awaitStartup()
            advanceRobolectricLooper()

            val activity = controller.get()
            val restored = activity.analyticsDialog()
            assertNotSame(original, restored)
            assertTrue(restored.getCheckBoxPrompt().isChecked)
        }

        controller.get().analyticsDialog().cancel()
        advanceRobolectricLooper()
        assertTrue(
            controller
                .get()
                .supportFragmentManager.fragments
                .none { it is DeckPickerAnalyticsOptInDialog },
        )
        assertEquals(0, controller.get().supportFragmentManager.backStackEntryCount)
    }

    private fun DeckPicker.analyticsDialog(): AlertDialog {
        val fragment = supportFragmentManager.fragments.filterIsInstance<DeckPickerAnalyticsOptInDialog>().single()
        return assertIs<AlertDialog>(fragment.requireDialog()).also { assertTrue(it.isShowing) }
    }
}
