// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.app.Application
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.testutils.registerTestApplicationSingletons

/**
 * A performance improvement to be used to avoid the startup cost of [AnkiDroidApp].
 *
 * usage:
 *
 * ```kt
 * @Config(application = EmptyApplication::class)
 * @Category(EmptyApplicationCategory::class)
 * ```
 *
 * test with:
 *
 * ```bash
 * ./gradlew testFullDebugUnitTest -PemptyApplication
 * ```
 */
class EmptyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // reset the static state of the app
        AnkiDroidApp.simulateRestoreFromBackup()

        // EmptyApplication skips AnkiDroidApp.onCreate, so register the global singletons that
        // activity code relies on (e.g. Animations, used by `Context.animationDisabled()`)
        registerTestApplicationSingletons()
    }
}
