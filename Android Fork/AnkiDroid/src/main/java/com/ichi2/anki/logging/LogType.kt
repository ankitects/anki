// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.logging

import com.ichi2.anki.BuildConfig
import com.ichi2.anki.common.utils.android.isRobolectric
import timber.log.Timber

enum class LogType {
    /** @see Timber.DebugTree */
    DEBUG,

    /** @see RobolectricDebugTree */
    ROBOLECTRIC,

    /** @see ProductionCrashReportingTree */
    PRODUCTION,

    ;

    companion object {
        val value: LogType
            get() {
                if (!BuildConfig.DEBUG) return PRODUCTION
                return if (isRobolectric) ROBOLECTRIC else DEBUG
            }
    }
}
