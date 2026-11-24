// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.CollectionOpenFailure

/**
 * AnkiDroidApp which raises [CollectionOpenFailure.FATAL_ERROR]
 */
class AnkiDroidAppWithFatalError : AnkiDroidApp() {
    override fun onCreate() {
        CollectionManager.emulatedOpenFailure = CollectionOpenFailure.FATAL_ERROR
        super.onCreate()
    }
}
