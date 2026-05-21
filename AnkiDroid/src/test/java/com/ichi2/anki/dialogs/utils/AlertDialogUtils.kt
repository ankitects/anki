/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.dialogs.utils

import android.content.DialogInterface
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.android.HandlerUtils.executeFunctionUsingHandler
import com.ichi2.utils.getInputField
import org.hamcrest.MatcherAssert.assertThat
import kotlin.test.assertNotNull

var AlertDialog.input
    get() = getInputField().text.toString()
    set(value) {
        getInputField().setText(value)
    }

val AlertDialog.title
    get() =
        requireNotNull(this.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)) {
            "androidx.appcompat.R.id.alertTitle not found"
        }.text.toString()

val AlertDialog.message
    get() =
        requireNotNull(this.findViewById<TextView>(android.R.id.message)) {
            "android.R.id.message not found"
        }.text.toString()

val AlertDialog.ankiListView
    get() =
        requireNotNull(this.listView ?: findViewById(R.id.list_view)) { "ankiListView not found" }

fun AlertDialog.performPositiveClick() {
    // This exists as callOnClick did not call the listener
    val positiveButton = assertNotNull(getButton(DialogInterface.BUTTON_POSITIVE), message = "positive button")
    assertThat("button is visible", positiveButton.isVisible)
    assertThat("button is enabled", positiveButton.isEnabled)
    executeFunctionUsingHandler { positiveButton.callOnClick() }
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
}
