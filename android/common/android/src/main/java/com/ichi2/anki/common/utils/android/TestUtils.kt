// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.android

import android.os.Build

val isRobolectric get() = Build.FINGERPRINT?.startsWith("robolectric") ?: false

/** Running under instrumentation. A "/androidTest" directory is created which contains a test collection. */
var isInstrumentationTest = false
