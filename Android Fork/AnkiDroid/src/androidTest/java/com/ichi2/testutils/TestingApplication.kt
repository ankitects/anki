// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.content.Context
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.common.utils.android.isInstrumentationTest

/**
 * An application which sets [com.ichi2.anki.common.utils.android.isInstrumentationTest] to true
 * so a test collection path is used
 */
class TestingApplication : AnkiDroidApp() {
    override fun attachBaseContext(base: Context) {
        isInstrumentationTest = true
        super.attachBaseContext(base)
    }
}
