// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Sanjay Sargam <sargamsanjaykumar@gmail.com>

package com.ichi2.anki

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import com.ichi2.anki.common.android.themes.disableXiaomiForceDarkMode
import com.ichi2.anki.workarounds.AppLoadedFromBackupWorkaround.showedActivityFailedScreen
import com.ichi2.themes.Themes

/**
 * This class is an abstract base class that extends Activity and provides common initialization logic for [IntentHandler] and [IntentHandler2].
 * By centralizing common setup tasks here, it promotes code reuse.
 */
abstract class AbstractIntentHandler : Activity() {
    /** Stop intent dispatch when Android's backup mode has skipped application and provider initialization. */
    protected fun showedActivityFailedScreen(savedInstanceState: Bundle?) =
        showedActivityFailedScreen(
            savedInstanceState = savedInstanceState,
            activitySuperOnCreate = { state ->
                // fixes: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
                // on Importer
                Themes.setTheme(this, state)
                super.onCreate(state)
            },
        )

    override fun attachBaseContext(newBase: Context) {
        val base = AnkiDroidApp.wrapForActiveProfile(newBase)
        Themes.updateCurrentTheme(base)
        super.attachBaseContext(base)
        val nightMode = if (Themes.isNightTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        applyOverrideConfiguration(
            Configuration().apply {
                fontScale = 0f
                uiMode = nightMode
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Themes.setTheme(this, savedInstanceState)
        disableXiaomiForceDarkMode(this)
        setContentView(R.layout.activity_progress_bar)
    }
}
