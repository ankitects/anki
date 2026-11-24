// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

package com.ichi2.widget.deckpicker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.view.View.MeasureSpec
import android.widget.RemoteViews
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.destinations.DeckOptionsDestination
import com.ichi2.anki.common.destinations.DeferredNavigation
import com.ichi2.anki.common.destinations.ReviewDeckDestination
import com.ichi2.anki.common.destinations.WidgetConfigDestination
import com.ichi2.anki.common.destinations.toIntent
import com.ichi2.anki.isCollectionEmpty
import com.ichi2.anki.launchCatching
import com.ichi2.anki.widgets.R
import com.ichi2.widget.ACTION_UPDATE_WIDGET
import com.ichi2.widget.AnalyticsWidgetProvider
import com.ichi2.widget.AppWidgetId
import com.ichi2.widget.AppWidgetId.Companion.INVALID_APPWIDGET_ID
import com.ichi2.widget.AppWidgetId.Companion.getAppWidgetId
import com.ichi2.widget.AppWidgetIds
import com.ichi2.widget.DayRolloverAlarm
import com.ichi2.widget.cancelRecurringAlarm
import com.ichi2.widget.getAppWidgetIdsEx
import com.ichi2.widget.getAppWidgetManager
import com.ichi2.widget.setRecurringAlarm
import com.ichi2.widget.sizedRemoteViews
import com.ichi2.widget.updateAppWidget
import timber.log.Timber
import kotlin.math.roundToInt

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

        private const val CORNER_RADIUS_DP = 28

        /**
         * Updates the widget with the deck data.
         *
         * This method replaces the view content with as many complete deck rows as fit
         * in the widget. Deleted decks are ignored.
         *
         * @param context the context of the application
         * @param appWidgetManager the AppWidgetManager instance
         * @param appWidgetId the ID of the app widget
         * @param deckIds the array of deck IDs to be displayed in the widget.
         *                The order is preserved when choosing which decks fit.
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
            applicationScope.launchCatching(errorMessageHandler = {}) {
                val isCollectionEmpty = isCollectionEmpty()
                if (isCollectionEmpty) {
                    showEmptyCollection(context, appWidgetManager, appWidgetId, remoteViews)
                    return@launchCatching
                }

                val deckData = getDeckNamesAndStats(deckIds.toList())

                if (deckData.isEmpty()) {
                    showEmptyWidget(context, appWidgetManager, appWidgetId, remoteViews)
                    return@launchCatching
                }

                val options = appWidgetManager.getAppWidgetOptions(appWidgetId.id)
                appWidgetManager.updateAppWidget(appWidgetId, sizedRemoteViews(options) { createDeckViews(context, deckData, it) })
            }
        }

        private suspend fun createDeckViews(
            context: Context,
            deckData: List<DeckWidgetData>,
            size: SizeF,
        ): RemoteViews {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_deck_picker_large)
            val density = context.resources.displayMetrics.density
            val widthSpec =
                MeasureSpec.makeMeasureSpec(
                    (size.width * density).roundToInt(),
                    if (size.width > 0) MeasureSpec.EXACTLY else MeasureSpec.UNSPECIFIED,
                )
            // Some hosts do not supply dimensions until the first resize.
            val availableHeight = if (size.height > 0) size.height * density else Float.POSITIVE_INFINITY
            var usedHeight = 0
            remoteViews.removeAllViews(R.id.deckCollection)
            for (deck in deckData) {
                val deckView = RemoteViews(context.packageName, R.layout.widget_item_deck_main)

                remoteViews.setViewVisibility(R.id.empty_widget, View.GONE)
                remoteViews.setViewVisibility(R.id.deckCollection, View.VISIBLE)
                deckView.setTextViewText(R.id.deckName, deck.name)
                deckView.setTextViewText(R.id.deckNew, deck.newCount.toString())
                deckView.setTextViewText(R.id.deckDue, deck.reviewCount.toString())
                deckView.setTextViewText(R.id.deckLearn, deck.learnCount.toString())

                // Measure the same content and layout the launcher will render, including
                // font scaling, fallback fonts (such as emoji), and vertical padding.
                val row = deckView.apply(context, null)
                row.measure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                if (usedHeight + row.measuredHeight > availableHeight) {
                    if (usedHeight > 0) break
                    // Keep one usable row when a large font cannot fit at the minimum widget height.
                    deckView.fitSingleRow(row, widthSpec, availableHeight.toInt())
                }
                usedHeight += row.measuredHeight

                val isEmptyDeck = deck.newCount == 0 && deck.reviewCount == 0 && deck.learnCount == 0

                val intent =
                    if (!isEmptyDeck) {
                        with(DeferredNavigation) { ReviewDeckDestination.ExternalLaunch(deck.deckId).toIntent() }
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

                deckView.setOnClickPendingIntent(R.id.widget_deck_row, pendingIntent)
                remoteViews.addView(R.id.deckCollection, deckView)
            }

            val cornersTooRound = size.height > 0 && size.height < CORNER_RADIUS_DP * 3
            remoteViews.setBackgrounds(
                if (cornersTooRound) {
                    R.drawable.widget_deck_picker_rounded_background_small
                } else {
                    R.drawable.widget_deck_picker_rounded_background
                },
            )
            return remoteViews
        }

        private fun RemoteViews.setBackgrounds(
            @DrawableRes background: Int,
        ) {
            setInt(android.R.id.background, "setBackgroundResource", background)
            setInt(R.id.deckCollection, "setBackgroundResource", background)
        }

        /** Shrink the first row only when its configured text size cannot fit in the widget. */
        private fun RemoteViews.fitSingleRow(
            row: View,
            widthSpec: Int,
            height: Int,
        ) {
            val textViews = listOf(R.id.deckName, R.id.deckNew, R.id.deckLearn, R.id.deckDue).map { row.findViewById<TextView>(it) }
            val originalSizes = textViews.map { it.textSize }
            val largestTextSize = originalSizes.max()

            fun measureAtScale(scale: Float) {
                textViews.zip(originalSizes).forEach { (view, textSize) ->
                    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize * scale)
                }
                row.measure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            }

            // Find the largest fitting scale to within one pixel, preserving the relative text sizes.
            var minScale = 0f
            var maxScale = 1f
            while ((maxScale - minScale) * largestTextSize > 1f) {
                val scale = (minScale + maxScale) / 2
                measureAtScale(scale)
                if (row.measuredHeight <= height) minScale = scale else maxScale = scale
            }
            measureAtScale(minScale)
            for (view in textViews) {
                setTextViewTextSize(view.id, TypedValue.COMPLEX_UNIT_PX, view.textSize)
            }
        }

        private fun showEmptyCollection(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: AppWidgetId,
            remoteViews: RemoteViews,
        ) {
            remoteViews.setTextViewText(R.id.empty_widget, context.getString(CommonString.empty_collection_state_in_widget))
            remoteViews.setViewVisibility(R.id.empty_widget, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.deckCollection, View.GONE)
            remoteViews.setBackgrounds(R.drawable.widget_deck_picker_rounded_background)

            val configIntent = with(DeferredNavigation) { WidgetConfigDestination.DeckPicker(appWidgetId.id).toIntent() }
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
            remoteViews.setTextViewText(R.id.empty_widget, context.getString(CommonString.empty_widget_state))
            remoteViews.setViewVisibility(R.id.empty_widget, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.deckCollection, View.GONE)
            remoteViews.setBackgrounds(R.drawable.widget_deck_picker_rounded_background)

            val configIntent = with(DeferredNavigation) { WidgetConfigDestination.DeckPicker(appWidgetId.id).toIntent() }
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
            val appWidgetManager = getAppWidgetManager(context) ?: return

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

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        DayRolloverAlarm.scheduleNext(context)
    }

    override fun performUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: AppWidgetIds,
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
        val appWidgetManager = getAppWidgetManager(context) ?: return
        super.onReceive(context, intent)

        val widgetPreferences = DeckPickerWidgetPreferences(context)

        when (intent.action) {
            ACTION_APPWIDGET_UPDATE -> {
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
                updateWidget(context, appWidgetManager, appWidgetId, selectedDeckIds)
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
