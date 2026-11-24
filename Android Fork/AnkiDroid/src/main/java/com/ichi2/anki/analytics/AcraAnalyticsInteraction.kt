// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.analytics

import android.content.Context
import com.google.auto.service.AutoService
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.analytics.AnalyticsEvent
import org.acra.config.CoreConfiguration
import org.acra.interaction.ReportInteraction
import org.acra.util.Installation
import timber.log.Timber
import java.io.File

/**
 * This ACRA Extension sends an analytics hit during crash handling while ACRA is enabled.
 * Questions answered: "Number of ACRA reports sent", "ACRA vs Analytics count differences"
 * See <a href="https://github.com/ACRA/acra/wiki/Custom-Extensions">Custom Extensions</a>
 */
@AutoService(ReportInteraction::class)
@Suppress("unused") // Not unused. ACRA dynamically loads this class
class AcraAnalyticsInteraction : ReportInteraction {
    override fun performInteraction(
        context: Context,
        config: CoreConfiguration,
        reportFile: File,
    ): Boolean {
        // Send an analytics exception hit with a UUID to match
        Timber.e("ACRA handling crash, sending analytics exception report")
        // the install id identifies the matching ACRA report; CrashReported carries
        // it as the label, so it stays out of the action
        Analytics.send(AnalyticsEvent.CrashReported(installId = Installation.id(context)))
        return true
    }
}
