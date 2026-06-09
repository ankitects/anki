/*
 *  Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.widget.deckpicker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.IntentHandler.Companion.intentToReviewDeckFromShortcuts
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.destinations.DeckOptionsDestination
import com.ichi2.anki.common.destinations.DeferredNavigation
import com.ichi2.anki.common.destinations.toIntent
import com.ichi2.anki.isCollectionEmpty
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.pages.fromDeckId
import com.ichi2.widget.ACTION_UPDATE_WIDGET
import com.ichi2.widget.AnalyticsWidgetProvider
import com.ichi2.widget.AppWidgetId
import com.ichi2.widget.AppWidgetId.Companion.INVALID_APPWIDGET_ID
import com.ichi2.widget.AppWidgetId.Companion.getAppWidgetId
import com.ichi2.widget.AppWidgetIds
import com.ichi2.widget.DayRolloverAlarm
import com.ichi2.widget.cancelRecurringAlarm
import com.ichi2.widget.getAppWidgetIdsEx
import com.ichi2.widget.setRecurringAlarm
import com.ichi2.widget.updateAppWidget
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Data class representing the data for a deck displayed in the widget.
 *
 * @property deckId The ID of the deck.
 * @property name The name of the deck.
 * @property reviewCount The number of cards due for review.
 * @property learnCount The number of cards in the learning phase.
 * @property newCount The number of new cards.
 */
data class DeckWidgetData(
    val deckId: DeckId,
    val name: String,
    val reviewCount: Int,
    val learnCount: Int,
    val newCount: Int,
)

/**
 * This widget displays a list of decks with their respective new, learning, and review card counts.
 * It updates every minute.
 * It can be resized vertically & horizontally.
 * It allows user to open the reviewer directly by clicking on the deck same as deckpicker.
 * There is only one way to configure the widget i.e. while adding it on home screen,
 */
class DeckPickerWidget : AnalyticsWidgetProvider() {
    companion object {
        /**
         * Key used for passing the selected deck IDs in the intent extras.
         */
        const val EXTRA_SELECTED_DECK_IDS = "deck_picker_widget_selected_deck_ids"

        /**
         * Updates the widget with the deck data.
         *
         * This method replaces the entire view content with entries for each deck ID
         * provided in the `deckIds` array. If any decks are deleted,
         * they will be ignored, and only the rest of the decks will be displayed.
         *
         * @param context the context of the application
         * @param appWidgetManager the AppWidgetManager instance
         * @param appWidgetId the ID of the app widget
         * @param deckIds the array of deck IDs to be displayed in the widget.
         *                Each ID corresponds to a specific deck, and the view will
         *                contain exactly the decks whose IDs are in this list.
         *
         */
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: AppWidgetId,
            deckIds: LongArray?,
        ) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_deck_picker_large)
            if (deckIds == null || deckIds.isEmpty()) {
                showEmptyWidget(context, appWidgetManager, appWidgetId, remoteViews)
                return
            }
            applicationScope.launch {
                val isCollectionEmpty = isCollectionEmpty()
                if (isCollectionEmpty) {
                    showEmptyCollection(context, appWidgetManager, appWidgetId, remoteViews)
                    return@launch
                }

                val deckData = getDeckNamesAndStats(deckIds.toList())

                if (deckData.isEmpty()) {
                    showEmptyWidget(context, appWidgetManager, appWidgetId, remoteViews)
                    return@launch
                }

                showDeck(context, appWidgetManager, appWidgetId, remoteViews, deckIds)
            }
        }

        private suspend fun showDeck(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: AppWidgetId,
            remoteViews: RemoteViews,
            deckIds: LongArray,
        ) {
            remoteViews.removeAllViews(R.id.deckCollection)
            val deckData = getDeckNamesAndStats(deckIds.toList())
            for (deck in deckData) {
                val deckView = RemoteViews(context.packageName, R.layout.widget_item_deck_main)

                remoteViews.setViewVisibility(R.id.empty_widget, View.GONE)
                remoteViews.setViewVisibility(R.id.deckCollection, View.VISIBLE)
                deckView.setTextViewText(R.id.deckName, deck.name)
                deckView.setTextViewText(R.id.deckNew, deck.newCount.toString())
                deckView.setTextViewText(R.id.deckDue, deck.reviewCount.toString())
                deckView.setTextViewText(R.id.deckLearn, deck.learnCount.toString())

                val isEmptyDeck = deck.newCount == 0 && deck.reviewCount == 0 && deck.learnCount == 0

                val intent =
                    if (!isEmptyDeck) {
                        intentToReviewDeckFromShortcuts(context, deck.deckId)
                    } else {
                        with(DeferredNavigation) { DeckOptionsDestination.fromDeckId(deck.deckId).toIntent() }
                    }

                val pendingIntent =
                    PendingIntent.getActivity(
                        context,
                        deck.deckId.toInt(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )

                deckView.setOnClickPendingIntent(R.id.deckName, pendingIntent)
                remoteViews.addView(R.id.deckCollection, deckView)
            }

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }

        private fun showEmptyCollection(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: AppWidgetId,
            remoteViews: RemoteViews,
        ) {
            remoteViews.setTextViewText(R.id.empty_widget, context.getString(R.string.empty_collection_state_in_widget))
            remoteViews.setViewVisibility(R.id.empty_widget, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.deckCollection, View.GONE)

            val configIntent =
                Intent(context, DeckPickerWidgetConfig::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            val configPendingIntent =
                PendingIntent.getActivity(
                    context,
                    appWidgetId.id,
                    configIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            remoteViews.setOnClickPendingIntent(R.id.empty_widget, configPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }

        private fun showEmptyWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: AppWidgetId,
            remoteViews: RemoteViews,
        ) {
            remoteViews.setTextViewText(R.id.empty_widget, context.getString(R.string.empty_widget_state))
            remoteViews.setViewVisibility(R.id.empty_widget, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.deckCollection, View.GONE)

            val configIntent =
                Intent(context, DeckPickerWidgetConfig::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            val configPendingIntent =
                PendingIntent.getActivity(
                    context,
                    appWidgetId.id,
                    configIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            remoteViews.setOnClickPendingIntent(R.id.empty_widget, configPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }

        /**
         * Updates the Deck Picker Widgets based on the current state of the application.
         * It fetches the App Widget IDs and updates each widget with the associated deck IDs.
         */
        fun updateDeckPickerWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)

            val provider = ComponentName(context, DeckPickerWidget::class.java)
            Timber.d("Fetching appWidgetIds for provider: ${provider.shortClassName}")

            val appWidgetIds = appWidgetManager.getAppWidgetIdsEx(provider)
            Timber.d("AppWidgetIds to update: ${appWidgetIds.joinToString(", ")}")

            for (appWidgetId in appWidgetIds) {
                val widgetPreferences = DeckPickerWidgetPreferences(context)
                val deckIds = widgetPreferences.getSelectedDeckIdsFromPreferences(appWidgetId)
                updateWidget(context, appWidgetManager, appWidgetId, deckIds)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        DayRolloverAlarm.scheduleNext(context)
    }

    override fun performUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: AppWidgetIds,
        usageAnalytics: UsageAnalytics,
    ) {
        Timber.d("Performing widget update for appWidgetIds: %s", appWidgetIds)

        val widgetPreferences = DeckPickerWidgetPreferences(context)

        for (widgetId in appWidgetIds) {
            Timber.d("Updating widget with ID: $widgetId")
            val selectedDeckIds = widgetPreferences.getSelectedDeckIdsFromPreferences(widgetId)

            /*
             * Explanation of behavior when selectedDeckIds is empty
             * If selectedDeckIds is empty, the widget will retain the previous deck list.
             * This behavior ensures that the widget does not display an empty view, which could be
             * confusing to the user. Instead, it maintains the last known state until a new valid
             * list of deck IDs is provided. This approach prioritizes providing a consistent
             * user experience over showing an empty or default state.
             */
            if (selectedDeckIds.isNotEmpty()) {
                Timber.d("Selected deck IDs: ${selectedDeckIds.joinToString(", ")} for widget ID: $widgetId")
                updateWidget(context, appWidgetManager, widgetId, selectedDeckIds)
            }
            setRecurringAlarm(context, widgetId, DeckPickerWidget::class.java)
        }

        Timber.d("Widget update process completed for appWidgetIds: ${appWidgetIds.joinToString(", ")}")
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)

        val widgetPreferences = DeckPickerWidgetPreferences(context)

        when (intent.action) {
            ACTION_APPWIDGET_UPDATE -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)

                // Retrieve the widget ID from the intent
                val appWidgetId = intent.getAppWidgetId()
                val selectedDeckIds = intent.getLongArrayExtra(EXTRA_SELECTED_DECK_IDS)

                Timber.d(
                    "Received ACTION_APPWIDGET_UPDATE with widget ID: $appWidgetId and selectedDeckIds: ${selectedDeckIds?.joinToString(
                        ", ",
                    )}",
                )

                if (appWidgetId != INVALID_APPWIDGET_ID && selectedDeckIds != null) {
                    Timber.d("Updating widget with ID: $appWidgetId")
                    updateWidget(context, appWidgetManager, appWidgetId, selectedDeckIds)
                    Timber.d("Widget update process completed for widget ID: $appWidgetId")
                }
            }
            // This custom action is received to update a specific widget.
            // It is triggered by the setRecurringAlarm method to refresh the widget's data periodically.
            ACTION_UPDATE_WIDGET -> {
                val appWidgetId = intent.getAppWidgetId()
                if (appWidgetId == INVALID_APPWIDGET_ID) {
                    return
                }

                val selectedDeckIds = widgetPreferences.getSelectedDeckIdsFromPreferences(appWidgetId)
                if (selectedDeckIds.isEmpty()) {
                    /*
                     * Rationale: see `performUpdate`
                     */
                    Timber.d(
                        "Ignoring ACTION_UPDATE_WIDGET for widget ID: $appWidgetId because selectedDeckIds is empty",
                    )
                    return
                }

                Timber.d(
                    "Updating widget with ID: $appWidgetId on ACTION_UPDATE_WIDGET. selectedDeckIds: ${
                        selectedDeckIds.joinToString(", ")
                    }",
                )
                updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId, selectedDeckIds)
            }
            AppWidgetManager.ACTION_APPWIDGET_DELETED -> {
                Timber.d("ACTION_APPWIDGET_DELETED received")
                val appWidgetId = intent.getAppWidgetId()
                if (appWidgetId != INVALID_APPWIDGET_ID) {
                    Timber.d("Deleting widget with ID: $appWidgetId")
                    cancelRecurringAlarm(context, appWidgetId, DeckPickerWidget::class.java)
                    widgetPreferences.deleteDeckData(appWidgetId)
                } else {
                    Timber.e("Invalid widget ID received in ACTION_APPWIDGET_DELETED")
                }
            }
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                Timber.d("ACTION_APPWIDGET_OPTIONS_CHANGED received from DeckPickerWidget")
            }
            AppWidgetManager.ACTION_APPWIDGET_ENABLED -> {
                Timber.d("Widget enabled")
            }
            AppWidgetManager.ACTION_APPWIDGET_DISABLED -> {
                Timber.d("Widget disabled")
            }
            else -> {
                Timber.e("Unexpected action received: ${intent.action}")
                CrashReportService.sendExceptionReport(
                    Exception("Unexpected action received: ${intent.action}"),
                    "DeckPickerWidget - onReceive",
                    onlyIfSilent = true,
                )
            }
        }
    }

    override fun onDeleted(
        context: Context?,
        appWidgetIds: IntArray?,
    ) {
        if (context == null) {
            Timber.w("Context is null in onDeleted")
            return
        }

        val widgetPreferences = DeckPickerWidgetPreferences(context)

        AppWidgetIds.of(appWidgetIds)?.forEach { widgetId ->
            cancelRecurringAlarm(context, widgetId, DeckPickerWidget::class.java)
            widgetPreferences.deleteDeckData(widgetId)
        }
    }
}

/**
 * Map deck id to the associated DeckPickerWidgetData. Omits any id that does not correspond to a deck.
 *
 * Note: This operation may be slow, as it involves processing the entire deck collection.
 *
 * @param deckId the list of deck ID to retrieve data for
 * @return a list of DeckPickerWidgetData objects containing deck names and statistics
 */
suspend fun getDeckNameAndStats(deckId: DeckId): DeckWidgetData? = getDeckNamesAndStats(listOf(deckId)).getOrNull(0)

suspend fun getDeckNamesAndStats(deckIds: List<DeckId>): List<DeckWidgetData> {
    val result = mutableListOf<DeckWidgetData>()

    val deckTree = withCol { sched.deckDueTree() }

    deckTree.forEach { node ->
        if (node.did !in deckIds) return@forEach
        result.add(
            DeckWidgetData(
                deckId = node.did,
                name = node.lastDeckNameComponent,
                reviewCount = node.revCount,
                learnCount = node.lrnCount,
                newCount = node.newCount,
            ),
        )
    }

    val deckIdToData = result.associateBy { it.deckId }
    return deckIds.mapNotNull { deckIdToData[it] }
}
