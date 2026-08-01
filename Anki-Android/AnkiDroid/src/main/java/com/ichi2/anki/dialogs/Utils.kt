/*
 * Copyright (c) 2022 Oakkitten
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
package com.ichi2.anki.dialogs

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/**
 * Render the alert dialog constructed clickable.
 * @return The dialog
 */
/* As far as understood, making links clickable should be done on the Alert before being shown.
 * So it must be the last call on the builder.
 */
fun AlertDialog.Builder.makeLinksClickable() = create().makeLinksClickable()

fun AlertDialog.makeLinksClickable() =
    apply {
        setOnShowListener {
            findViewById<TextView>(android.R.id.message)
                ?.movementMethod = LinkMovementMethod.getInstance()
        }
    }
