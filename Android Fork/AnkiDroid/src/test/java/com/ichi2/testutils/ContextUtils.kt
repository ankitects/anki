// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Returns a [Context] whose configuration uses a right-to-left layout direction */
fun Context.createRtlContext(): Context {
    val rtlConfiguration =
        Configuration(resources.configuration).apply {
            setLayoutDirection(Locale.forLanguageTag("ar"))
        }
    return createConfigurationContext(rtlConfiguration)
}
