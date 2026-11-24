// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.ExportReadyDialog.Companion.REQUEST_EXPORT_SAVE
import com.ichi2.anki.dialogs.ExportReadyDialog.Companion.REQUEST_EXPORT_SHARE
import com.ichi2.anki.dialogs.ExportReadyDialog.Companion.RESULT_EXPORT_PATH
import com.ichi2.anki.multimedia.clearFileProviderRoots
import com.ichi2.anki.setIntroductionSlidesShown
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.ShadowFileProviderPathStrategy
import com.ichi2.testutils.ext.snackbarText
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowFileProviderPathStrategy::class])
class BackupPromptDialogTest : RobolectricTest() {
    @Before
    fun keepCollectionInAppStorage() {
        val appStorage = assertNotNull(targetContext.getExternalFilesDir(null))
        getPreferences().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, appStorage.absolutePath) }
    }

    @Before
    fun forgetOldShareRoots() = clearFileProviderRoots()

    @Test
    fun `saving a collection package postpones the reminder`() {
        val deckPicker = startDeckPicker()

        deckPicker.saveExport("collection.colpkg")

        assertFalse(deckPicker.showsBackupReminder())
    }

    @Test
    fun `opening the share sheet keeps the reminder`() {
        val deckPicker = startDeckPicker()

        deckPicker.shareExport("collection.colpkg")

        // No recipient has been chosen and the file remains in externalCacheDir.
        assertTrue(deckPicker.showsBackupReminder(), "Opening the chooser without a recipient must not count as a backup")
    }

    @Test
    fun `choosing a share target postpones the reminder`() {
        val deckPicker = startDeckPicker()

        deckPicker.shareExport("collection.colpkg").chooseTarget(ComponentName("com.ichi2.testutils.drive", "UploadActivity"))

        assertFalse(deckPicker.showsBackupReminder())
    }

    @Test
    fun `sharing to AnkiDroid keeps the reminder`() {
        val deckPicker = startDeckPicker()

        deckPicker.shareExport("collection.colpkg").chooseTarget(ComponentName(targetContext, IntentHandler::class.java))

        assertTrue(deckPicker.showsBackupReminder())
    }

    @Test
    fun `a share result without an app keeps the reminder`() {
        val deckPicker = startDeckPicker()

        deckPicker.shareExport("collection.colpkg").sendChooserResult(Intent())

        assertTrue(deckPicker.showsBackupReminder())
    }

    @Test
    fun `saving a deck package keeps the reminder`() {
        val deckPicker = startDeckPicker()

        deckPicker.saveExport("deck.apkg")

        assertTrue(deckPicker.showsBackupReminder())
    }

    private fun startDeckPicker(): DeckPicker {
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        return startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
    }

    private fun DeckPicker.saveExport(name: String) {
        supportFragmentManager.setFragmentResult(
            REQUEST_EXPORT_SAVE,
            Bundle().apply { putString(RESULT_EXPORT_PATH, createExport(name).path) },
        )
        val picker = assertNotNull(shadowOf(this).nextStartedActivity)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.action)
        shadowOf(this).receiveResult(picker, Activity.RESULT_OK, Intent().setData(Uri.fromFile(File(tempFolder.root, name))))
        val saved = getString(CommonString.export_save_apkg_successful)
        advanceRobolectricLooperUntil { snackbarText == saved }
    }

    private fun DeckPicker.shareExport(name: String): Intent {
        registerShareSheet()
        supportFragmentManager.setFragmentResult(
            REQUEST_EXPORT_SHARE,
            Bundle().apply { putString(RESULT_EXPORT_PATH, createExport(name).path) },
        )
        val chooser = assertNotNull(shadowOf(this).nextStartedActivity)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        return chooser
    }

    private fun Intent.chooseTarget(target: ComponentName) = sendChooserResult(Intent().putExtra(Intent.EXTRA_CHOSEN_COMPONENT, target))

    @SuppressLint("InlinedApi")
    private fun Intent.sendChooserResult(result: Intent) {
        val sender =
            listOf(Intent.EXTRA_CHOOSER_RESULT_INTENT_SENDER, Intent.EXTRA_CHOSEN_COMPONENT_INTENT_SENDER)
                .firstNotNullOfOrNull { IntentCompat.getParcelableExtra(this, it, IntentSender::class.java) }
        assertNotNull(sender).sendIntent(targetContext, Activity.RESULT_OK, result, null, null)
        advanceRobolectricLooper()
    }

    private fun DeckPicker.createExport(name: String) =
        File(externalCacheDir, "export/$name").apply {
            parentFile?.mkdirs()
            writeText(name)
        }

    private fun registerShareSheet() {
        val chooser = ComponentName("com.ichi2.testutils.share", "ChooserActivity")
        shadowOf(targetContext.packageManager).apply {
            addActivityIfNotPresent(chooser).exported = true
            addIntentFilterForActivity(chooser, IntentFilter(Intent.ACTION_CHOOSER).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        }
    }

    private fun DeckPicker.showsBackupReminder() = runBlocking { BackupPromptDialog.showIfAvailable(this@showsBackupReminder) }
}
