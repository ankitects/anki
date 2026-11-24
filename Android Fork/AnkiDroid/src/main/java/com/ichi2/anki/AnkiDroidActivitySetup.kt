// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.ichi2.themes.Themes

internal object AnkiDroidActivitySetup : ActivitySetup {
    override fun wrapForActiveProfile(base: Context) = AnkiDroidApp.wrapForActiveProfile(base)

    override fun applyNightMode(context: Context) = Themes.updateCurrentTheme(context)

    override fun setTheme(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Themes.setTheme(activity, savedInstanceState)
}

fun initializeActivitySetup() {
    ActivitySetup.register(AnkiDroidActivitySetup)
}
