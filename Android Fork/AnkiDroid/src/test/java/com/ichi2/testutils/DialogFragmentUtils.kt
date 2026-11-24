// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton

/** The positive button of the [AlertDialog] shown by a [DialogFragment] */
val DialogFragment.positiveButton: Button
    get() = (requireDialog() as AlertDialog).positiveButton

/** The neutral button of the [AlertDialog] shown by a [DialogFragment] */
val DialogFragment.neutralButton: Button
    get() = requireNotNull((requireDialog() as AlertDialog).neutralButton)
