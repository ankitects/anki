// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.content.Context
import android.os.Bundle

var activitySetup: ActivitySetup? = null
    private set

interface ActivitySetup {
    fun wrapForActiveProfile(base: Context): Context

    fun applyNightMode(context: Context)

    fun setTheme(
        activity: Activity,
        savedInstanceState: Bundle?,
    )

    companion object {
        fun register(setup: ActivitySetup) {
            activitySetup = setup
        }
    }
}
