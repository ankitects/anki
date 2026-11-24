// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import com.ichi2.anki.common.destinations.WidgetConfigDestination
import com.ichi2.widget.cardanalysis.CardAnalysisWidgetConfig
import com.ichi2.widget.deckpicker.DeckPickerWidgetConfig

fun WidgetConfigDestination.toIntent(context: Context): Intent {
    val activity =
        when (this) {
            is WidgetConfigDestination.CardAnalysis -> CardAnalysisWidgetConfig::class.java
            is WidgetConfigDestination.DeckPicker -> DeckPickerWidgetConfig::class.java
        }
    return Intent(context, activity).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
}
