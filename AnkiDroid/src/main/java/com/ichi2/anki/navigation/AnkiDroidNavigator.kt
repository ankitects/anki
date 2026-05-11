// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.navigation

import android.app.Application
import android.content.Context
import android.content.Intent
import com.ichi2.anki.common.destinations.Destination
import com.ichi2.anki.common.destinations.Navigator

/** AnkiDroid's [Navigator] implementation. */
object AnkiDroidNavigator : Navigator {
    private lateinit var appContext: Context

    fun initialize(application: Application) {
        appContext = application
    }

    override fun toIntent(destination: Destination): Intent = error("no Intent mapping registered for $destination")
}

/** Initializes the AnkiDroid navigator and wires it up as the global [Navigator]. */
context(application: Application)
fun initializeNavigator() {
    AnkiDroidNavigator.initialize(application)
    Navigator.register(AnkiDroidNavigator)
}
