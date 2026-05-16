// SPDX-FileCopyrightText: 2026 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.account

import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.settings.Prefs
import org.junit.Test

class AccountActivityScreenshotTest : ScreenshotTest() {
    @Test
    fun loggedIn() {
        Prefs.hkey = "my precious hkey"
        Prefs.username = "lovely@example.com"
        val intent = AccountActivity.getIntent(targetContext)

        ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
            scenario.onActivity {
                captureScreen("logged_in")
            }
        }
    }

    @Test
    fun loggedOut() {
        Prefs.hkey = ""
        val intent = AccountActivity.getIntent(targetContext)

        ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
            scenario.onActivity {
                captureScreen("logged_out")
            }
        }
    }
}
