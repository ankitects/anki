// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.analytics

import androidx.annotation.LayoutRes
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.common.analytics.Analytics

abstract class AnalyticsDialogFragment(
    @LayoutRes contentLayoutId: Int = 0,
) : DialogFragment(contentLayoutId) {
    override fun onResume() {
        super.onResume()
        Analytics.sendAnalyticsScreenView(this)
    }
}
