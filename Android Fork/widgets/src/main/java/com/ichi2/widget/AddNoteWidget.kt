// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import androidx.core.app.PendingIntentCompat
import com.ichi2.anki.common.destinations.DeferredNavigation
import com.ichi2.anki.common.destinations.NoteEditorDestination
import com.ichi2.anki.common.destinations.toIntent
import com.ichi2.anki.widgets.R

class AddNoteWidget : AnalyticsWidgetProvider() {
    override val requiresCollection = false

    override fun performUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: AppWidgetIds,
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        /**
         * Updates the widgets displayed in the provided context using the given AppWidgetManager
         * and widget IDs, setting up an intent to open the NoteEditor with the caller as the deck picker.
         *
         * @param context The context in which the widgets are updated.
         * @param appWidgetManager The AppWidgetManager instance used to update widgets.
         * @param appWidgetIds The array of widget IDs to be updated.
         */
        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: AppWidgetIds,
        ) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_add_note)
            val intent = with(DeferredNavigation) { NoteEditorDestination.AddNoteWithSplash.toIntent() }
            val pendingIntent = PendingIntentCompat.getActivity(context, 0, intent, 0, false)
            remoteViews.setOnClickPendingIntent(R.id.widget_add_note_button, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetIds, remoteViews)
        }
    }
}
