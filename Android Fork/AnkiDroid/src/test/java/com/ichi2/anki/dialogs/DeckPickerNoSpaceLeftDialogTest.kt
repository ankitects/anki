// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.dialogs

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.BackupManager
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.awaitStartup
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.setIntroductionSlidesShown
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.ShadowStatFs
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.hasSize
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerNoSpaceLeftDialogTest : RobolectricTest() {
    @Before
    fun setUpStorageFull() {
        setIntroductionSlidesShown(true)
        val ankiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(targetContext)
        ShadowStatFs.registerStats(ankiDroidDirectory.parentFile!!, blockCount = 100, freeBlocks = 0, availableBlocks = 0)
        assertFalse(BackupManager.enoughDiscSpace(ankiDroidDirectory))
    }

    @After
    fun resetStorage() = BackupManagerTestUtilities.reset()

    @Test
    fun `one tap on OK closes the storage full dialog`() {
        val deckPicker = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).get()

        deckPicker.tapOkOnStorageFullDialog()

        assertThat(deckPicker.storageFullDialogs(), empty())
    }

    @Test
    fun `one tap on OK closes the storage full dialog after the deck picker is recreated`() {
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())

        repeat(2) {
            controller.recreate()
            controller.get().viewModel.awaitStartup()
            advanceRobolectricLooper()
        }

        controller.get().tapOkOnStorageFullDialog()

        assertThat(controller.get().storageFullDialogs(), empty())
    }

    private fun DeckPicker.tapOkOnStorageFullDialog() {
        val dialogs = storageFullDialogs()
        assertThat(dialogs, hasSize(1))
        val dialog = assertIs<AlertDialog>(dialogs.single().requireDialog())
        assertTrue(dialog.isShowing)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()
    }

    private fun DeckPicker.storageFullDialogs() = supportFragmentManager.fragments.filterIsInstance<DeckPickerNoSpaceLeftDialog>()
}
