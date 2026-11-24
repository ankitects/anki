// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.navigation

import android.app.Application
import android.content.Intent
import com.ichi2.anki.browser.toIntent
import com.ichi2.anki.common.destinations.BrowserDestination
import com.ichi2.anki.common.destinations.CardInfoDestination
import com.ichi2.anki.common.destinations.ChangelogDestination
import com.ichi2.anki.common.destinations.CsvImporterDestination
import com.ichi2.anki.common.destinations.DeckOptionsDestination
import com.ichi2.anki.common.destinations.Destination
import com.ichi2.anki.common.destinations.LauncherDestination
import com.ichi2.anki.common.destinations.Navigator
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.destinations.PreferencesDestination
import com.ichi2.anki.common.destinations.ReviewDeckDestination
import com.ichi2.anki.common.destinations.StatisticsDestination
import com.ichi2.anki.common.destinations.StudyOptionsDestination
import com.ichi2.anki.common.destinations.WidgetConfigDestination
import com.ichi2.anki.noteeditor.toIntent
import com.ichi2.anki.pages.toIntent
import com.ichi2.anki.preferences.toIntent
import com.ichi2.anki.toIntent
import com.ichi2.widget.toIntent

/** AnkiDroid's [Navigator] implementation. */
object AnkiDroidNavigator : Navigator {
    private lateinit var navContext: Application

    fun initialize(application: Application) {
        navContext = application
    }

    override fun toIntent(destination: Destination): Intent =
        when (destination) {
            is BrowserDestination -> destination.toIntent(navContext)
            is CardInfoDestination -> destination.toIntent(navContext)
            is NoteEditorDestination -> destination.toIntent(navContext)
            is CsvImporterDestination -> destination.toIntent(navContext)
            is DeckOptionsDestination -> destination.toIntent(navContext)
            is ChangelogDestination -> destination.toIntent(navContext)
            is LauncherDestination -> destination.toIntent(navContext)
            is PreferencesDestination -> destination.toIntent(navContext)
            is ReviewDeckDestination -> destination.toIntent(navContext)
            is StatisticsDestination -> destination.toIntent(navContext)
            is StudyOptionsDestination -> destination.toIntent(navContext)
            is WidgetConfigDestination -> destination.toIntent(navContext)
        }
}

/** Initializes the AnkiDroid navigator and wires it up as the global [Navigator]. */
context(application: Application)
fun initializeNavigator() {
    AnkiDroidNavigator.initialize(application)
    Navigator.register(AnkiDroidNavigator)
}
