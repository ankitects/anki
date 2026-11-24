// SPDX-License-Identifier: GPL-3.0-or-later

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
