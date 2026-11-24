// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.common.android.Animations

/**
 * Sets whether [Animations.areSystemAnimationsEnabled].
 *
 * Robolectric resets the setting after each test.
 */
fun setSystemAnimationsEnabled(enabled: Boolean) {
    val contentResolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
    Settings.Global.putFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (enabled) 1f else 0f)
}
