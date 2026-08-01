/*
 * Copyright (c) 2022 lukstbit <lukstbit@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.app.Application
import leakcanary.AppWatcher.isInstalled
import leakcanary.AppWatcher.manualInstall
import leakcanary.LeakCanary.config
import leakcanary.LeakCanary.showLeakDisplayActivityLauncherIcon
import shark.AndroidReferenceMatchers
import shark.ReferenceMatcher

object LeakCanaryConfiguration {
    /**
     * Disable LeakCanary.
     */
    fun disable() {
        config =
            config.copy(
                dumpHeap = false,
                retainedVisibleThreshold = 0,
                referenceMatchers = AndroidReferenceMatchers.appDefaults,
                computeRetainedHeapSize = false,
                maxStoredHeapDumps = 0,
            )
    }

    /**
     * Sets the initial configuration for LeakCanary. This method can be used to match known library
     * leaks or leaks which have been already reported previously.
     */
    fun setInitialConfigFor(
        application: Application,
        knownMemoryLeaks: List<ReferenceMatcher> = emptyList(),
    ) {
        config = config.copy(referenceMatchers = AndroidReferenceMatchers.appDefaults + knownMemoryLeaks)
        // AppWatcher manual install if not already installed
        if (!isInstalled) {
            manualInstall(application)
        }
        // Show 'Leaks' app launcher. It has been removed by default via constants.xml.
        showLeakDisplayActivityLauncherIcon(true)
    }
}
