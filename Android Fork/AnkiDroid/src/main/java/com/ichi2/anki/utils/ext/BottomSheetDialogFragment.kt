// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

val BottomSheetDialogFragment.behavior
    get() =
        BottomSheetBehavior
            .from(
                requireDialog()
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet)!!,
            )
