// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText:  Copyright (c) 2022 Oakkitten

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
