/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.AsyncDialogFragment
import com.ichi2.anki.dialogs.ImportDialog
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckPickerImportTest : RobolectricTest() {
    @Test
    fun importAddShowsImportDialog() {
        val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())

        deckPicker.showImportDialog(ImportDialog.Type.DIALOG_IMPORT_ADD_CONFIRM, "")

        assertThat(deckPicker.getAsyncDialogFragmentClass(), Matchers.typeCompatibleWith(ImportDialog::class.java))
    }

    @Test
    fun replaceShowsImportDialog() {
        val deckPicker = super.startActivityNormallyOpenCollectionWithIntent(DeckPickerImport::class.java, Intent())

        deckPicker.showImportDialog(ImportDialog.Type.DIALOG_IMPORT_REPLACE_CONFIRM, "")

        assertThat(deckPicker.getAsyncDialogFragmentClass(), Matchers.typeCompatibleWith(ImportDialog::class.java))
    }

    private class DeckPickerImport : DeckPicker() {
        private var dialogFragment: AsyncDialogFragment? = null

        fun getAsyncDialogFragmentClass(): Class<*> {
            if (dialogFragment == null) {
                fail("No async fragment shown")
            }
            return dialogFragment!!.javaClass
        }

        override fun showAsyncDialogFragment(newFragment: AsyncDialogFragment) {
            dialogFragment = newFragment
            super.showAsyncDialogFragment(newFragment)
        }
    }
}
