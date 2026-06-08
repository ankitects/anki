// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.navigation

import android.app.Application
import android.content.Intent
import com.ichi2.anki.browser.toIntent
import com.ichi2.anki.common.destinations.BrowserDestination
import com.ichi2.anki.common.destinations.CsvImporterDestination
import com.ichi2.anki.common.destinations.Destination
import com.ichi2.anki.common.destinations.Navigator
import com.ichi2.anki.pages.toIntent

/** AnkiDroid's [Navigator] implementation. */
object AnkiDroidNavigator : Navigator {
    private lateinit var navContext: Application

    fun initialize(application: Application) {
        navContext = application
    }

    override fun toIntent(destination: Destination): Intent =
        when (destination) {
            is BrowserDestination -> destination.toIntent(navContext)
            is CsvImporterDestination -> destination.toIntent(navContext)
        }
}

/** Initializes the AnkiDroid navigator and wires it up as the global [Navigator]. */
context(application: Application)
fun initializeNavigator() {
    AnkiDroidNavigator.initialize(application)
    Navigator.register(AnkiDroidNavigator)
}
