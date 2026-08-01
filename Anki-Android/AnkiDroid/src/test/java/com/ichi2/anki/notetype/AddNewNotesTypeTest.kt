/*
 * Copyright (c) 2026 Chaitanya Medidar <2023.chaitanya.medidar@ves.ac.in>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.notetype

import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import kotlinx.coroutines.launch
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
class AddNewNotesTypeTest : RobolectricTest() {
    @Test
    fun `add note type - whitespace only name keeps OK button disabled`() =
        runTest {
            val activity = startRegularActivity<ManageNotetypes>()
            val addNewNotesType = AddNewNotesType(activity)
            activity.lifecycleScope.launch {
                addNewNotesType.showAddNewNotetypeDialog()
            }
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val nameInput = dialog.findViewById<EditText>(R.id.notetype_new_name)!!
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            // whitespace-only should disable the OK button
            nameInput.setText("   ")
            assertThat("OK button disabled for whitespace-only name", positiveButton.isEnabled, equalTo(false))

            // valid name should enable the button
            nameInput.setText("My New Note Type")
            assertThat("OK button enabled for a valid name", positiveButton.isEnabled, equalTo(true))

            // empty string should disable it again
            nameInput.setText("")
            assertThat("OK button disabled for empty name", positiveButton.isEnabled, equalTo(false))
        }
}
