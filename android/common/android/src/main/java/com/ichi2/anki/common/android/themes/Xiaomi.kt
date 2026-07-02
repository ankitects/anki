// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.android.themes

import android.content.Context
import com.ichi2.anki.common.android.R

/**
 * #8150: Fix icons not appearing in Note Editor due to MIUI 12's "force dark" mode
 */
fun disableXiaomiForceDarkMode(context: Context) {
    // Setting a theme is an additive operation, so this adds a single property.
    context.setTheme(R.style.ThemeOverlay_Xiaomi)
}
