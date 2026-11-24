// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.content.SharedPreferences
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.common.preferences.SharedPreferencesTestHook
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Isolate singleton preferences without constructing an Android application or loading resources.
 *
 * Keys resolve as in [inMemoryPrefs]: use [com.ichi2.anki.settings.Prefs.key] to address them.
 */
class JvmPreferencesRule : TestRule {
    val preferences: SharedPreferences = SPMockBuilder().createSharedPreferences()
    private val resources = idAsStringResources()

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val previous = SharedPreferencesTestHook.override
                val previousResources = AnkiDroidApp.resourcesTestingOverride
                try {
                    AnkiDroidApp.resourcesTestingOverride = resources
                    SharedPreferencesTestHook.override = preferences
                    base.evaluate()
                } finally {
                    SharedPreferencesTestHook.override = previous
                    AnkiDroidApp.resourcesTestingOverride = previousResources
                }
            }
        }
}
