// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutils

import android.app.Application
import com.ichi2.anki.common.android.Animations
import com.ichi2.anki.initializeActivitySetup
import com.ichi2.anki.initializeDatabaseErrorDialogs
import com.ichi2.anki.initializeWidgetRepository
import com.ichi2.anki.navigation.initializeNavigator
import com.ichi2.anki.settings.PrefsRepository
import com.ichi2.anki.startup.maybeSetDefaultCollectionPath
import com.ichi2.testutils.EmptyApplication
import com.ichi2.widget.AnkiDroidWidgetSmall
import com.ichi2.widget.WidgetStatus

/**
 * Registers the global singletons that [com.ichi2.anki.AnkiDroidApp.onCreate] wires up, for tests
 * which use [EmptyApplication] (and therefore skip `AnkiDroidApp.onCreate`).
 */
context(application: Application)
fun registerTestApplicationSingletons() {
    initializeNavigator()
    initializeWidgetRepository()
    initializeDatabaseErrorDialogs()
    initializeActivitySetup()
    WidgetStatus.register(PrefsRepository(application)) { context -> AnkiDroidWidgetSmall.UpdateService().doUpdate(context) }
    Animations.setPreferencesProvider { context -> PrefsRepository(context) }
    maybeSetDefaultCollectionPath(application)
}
