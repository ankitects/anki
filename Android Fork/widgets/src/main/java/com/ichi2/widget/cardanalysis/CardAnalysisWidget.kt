// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

package com.ichi2.widget.cardanalysis

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
import androidx.annotation.StringRes
import androidx.core.util.TypedValueCompat
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
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks.Companion.NOT_FOUND_DECK_ID
import com.ichi2.anki.widgets.R
import com.ichi2.anki.widgets.databinding.WidgetCardAnalysisBinding
import com.ichi2.widget.ACTION_UPDATE_WIDGET
import com.ichi2.widget.AnalyticsWidgetProvider
import com.ichi2.widget.AppWidgetId
import com.ichi2.widget.AppWidgetId.Companion.INVALID_APPWIDGET_ID
import com.ichi2.widget.AppWidgetId.Companion.getAppWidgetId
import com.ichi2.widget.AppWidgetIds
import com.ichi2.widget.DayRolloverAlarm
import com.ichi2.widget.cancelRecurringAlarm
import com.ichi2.widget.deckpicker.DeckWidgetData
import com.ichi2.widget.deckpicker.getDeckNameAndStats
import com.ichi2.widget.getAppWidgetIdsEx
import com.ichi2.widget.getAppWidgetManager
import com.ichi2.widget.setRecurringAlarm
import com.ichi2.widget.sizedRemoteViews
import com.ichi2.widget.updateAppWidget
import timber.log.Timber
import java.text.BreakIterator
import kotlin.math.roundToInt

/**
 * This widget displays a deck with the respective new, learning, and review card counts.
 * It updates every minute and if there is any changes in study queues.
 * It allows user to open the reviewer directly by clicking on the deck same as deckpicker.
 * It can be configured and reconfigured by holding the widget.
 */
class CardAnalysisWidget : AnalyticsWidgetProvider() {
    companion object {
        /**
         * Key used for passing the selected deck ID in the intent extras.
         */
        const val EXTRA_SELECTED_DECK_ID = "card_analysis_widget_selected_deck_id"

        private const val CORNER_RADIUS_DP = 28

        /**
         * Updates the widget with the deck data.
         *
         * This method updates the widget view content with the deck data corresponding
         * to the provided deck ID. If the deck is deleted, the widget will be show a message "Missing deck. Please reconfigure".
         *
         * @param context the context of the application
         * @param appWidgetManager the AppWidgetManager instance
         * @param appWidgetId the ID of the app widget
         */
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: AppWidgetId,
        ) {
            val deckId = getDeckIdForWidget(context, appWidgetId)

            if (deckId == NOT_FOUND_DECK_ID) {
                // If deckId is null, it means no deck was selected or the selected deck was deleted.
                // In this case, we don't save the null value to preferences because we want to
                // keep the previous deck ID if the user reconfigures the widget later.
                // Instead, we show a message prompting the user to reconfigure the widget.
                showMissingDeck(context, appWidgetManager, appWidgetId)
                return
            }

            applicationScope.launchCatching(errorMessageHandler = {}) {
                val isCollectionEmpty = isCollectionEmpty()
                if (isCollectionEmpty) {
                    showCollectionDeck(context, appWidgetManager, appWidgetId)
                    return@launchCatching
                }

                val deckData = getDeckNameAndStats(deckId)
                if (deckData == null) {
                    // The deck was found but no data could be fetched, so update the preferences to remove the deck.
                    // This ensures that the widget does not retain a reference to a non-existent or invalid deck.
                    CardAnalysisWidgetPreferences(context).saveSelectedDeck(appWidgetId, NOT_FOUND_DECK_ID)
                    showMissingDeck(context, appWidgetManager, appWidgetId)
                    return@launchCatching
                }
                showDeck(context, appWidgetManager, appWidgetId, deckData)
            }
        }

        private fun getDeckIdForWidget(
            context: Context,
            appWidgetId: AppWidgetId,
        ): DeckId {
            val widgetPreferences = CardAnalysisWidgetPreferences(context)
            return widgetPreferences.getSelectedDeckIdFromPreferences(appWidgetId) ?: NOT_FOUND_DECK_ID
        }

        private fun showCollectionDeck(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: AppWidgetId,
        ) = showMessage(
            context,
            appWidgetManager,
            appWidgetId,
            CommonString.empty_collection_state_in_widget,
            shorterMessage = CommonString.empty_collection_state_in_widget_short,
        )

        private fun showMissingDeck(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: AppWidgetId,
        ) = showMessage(
            context,
            appWidgetManager,
            appWidgetId,
            CommonString.empty_widget_state,
            shorterMessage = CommonString.empty_widget_state_short,
        )

        private fun showMessage(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: AppWidgetId,
            @StringRes message: Int,
            @StringRes shorterMessage: Int,
        ) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId.id)
            appWidgetManager.updateAppWidget(
                appWidgetId,
                sizedRemoteViews(options) {
                    createMessageViews(context, appWidgetId, context.getString(message), context.getString(shorterMessage), it)
                },
            )
        }

        private fun createMessageViews(
            context: Context,
            appWidgetId: AppWidgetId,
            message: String,
            shorterMessage: String,
            size: SizeF,
        ): RemoteViews {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_card_analysis)
            remoteViews.setViewVisibility(R.id.empty_widget, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.cardAnalysisDataHolder, View.GONE)
            remoteViews.setViewVisibility(R.id.deckNameCardAnalysis, View.GONE)
            remoteViews.setBackgrounds(size.cornersTooRound())

            val configIntent = with(DeferredNavigation) { WidgetConfigDestination.CardAnalysis(appWidgetId.id).toIntent() }
            val configPendingIntent =
                PendingIntent.getActivity(
                    context,
                    appWidgetId.id,
                    configIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            remoteViews.setOnClickPendingIntent(R.id.empty_widget, configPendingIntent)
            remoteViews.fitMessage(context, size, message, shorterMessage)
            return remoteViews
        }

        private suspend fun showDeck(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: AppWidgetId,
            deckData: DeckWidgetData,
        ) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId.id)
            appWidgetManager.updateAppWidget(appWidgetId, sizedRemoteViews(options) { createDeckViews(context, deckData, it) })
        }

        private suspend fun createDeckViews(
            context: Context,
            deckData: DeckWidgetData,
            size: SizeF,
        ): RemoteViews {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_card_analysis)
            remoteViews.setTextViewText(R.id.deckNameCardAnalysis, deckData.name)
            remoteViews.setTextViewText(R.id.deckNew_card_analysis_widget, deckData.newCount.toString())
            remoteViews.setTextViewText(R.id.deckDue_card_analysis_widget, deckData.reviewCount.toString())
            remoteViews.setTextViewText(R.id.deckLearn_card_analysis_widget, deckData.learnCount.toString())

            // Hide empty_widget and show the actual widget content
            remoteViews.setViewVisibility(R.id.empty_widget, View.GONE)
            remoteViews.setViewVisibility(R.id.cardAnalysisDataHolder, View.VISIBLE)
            remoteViews.setViewVisibility(R.id.deckNameCardAnalysis, View.VISIBLE)

            val isEmptyDeck = deckData.newCount == 0 && deckData.reviewCount == 0 && deckData.learnCount == 0

            val intent =
                if (!isEmptyDeck) {
                    with(DeferredNavigation) { ReviewDeckDestination.ExternalLaunch(deckData.deckId).toIntent() }
                } else {
                    with(DeferredNavigation) { DeckOptionsDestination.fromDeckId(deckData.deckId).toIntent() }
                }
            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    deckData.deckId.toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            remoteViews.setOnClickPendingIntent(R.id.deckNameCardAnalysis, pendingIntent)

            remoteViews.setBackgrounds(size.cornersTooRound())
            remoteViews.fitTo(context, size)
            return remoteViews
        }

        private fun SizeF.cornersTooRound() = height > 0 && height < CORNER_RADIUS_DP * 3

        private fun RemoteViews.setBackgrounds(cornersTooRound: Boolean) {
            setInt(
                android.R.id.background,
                "setBackgroundResource",
                if (cornersTooRound) {
                    R.drawable.widget_card_analysis_rounded_background_small
                } else {
                    R.drawable.widget_card_analysis_rounded_background
                },
            )
            setInt(
                R.id.cardAnalysisDataHolder,
                "setBackgroundResource",
                if (cornersTooRound) {
                    R.drawable.widget_card_analysis_rounded_inner_background_small
                } else {
                    R.drawable.widget_card_analysis_rounded_inner_background
                },
            )
        }

        /** Shrinks the text and padding evenly until the counts and deck name fit in [size] without splitting a word. */
        private fun RemoteViews.fitTo(
            context: Context,
            size: SizeF,
        ) {
            val widget = OffscreenWidget(context, this, size)
            val binding = WidgetCardAnalysisBinding.bind(widget.root)
            val counts = listOf(binding.deckNewCardAnalysisWidget, binding.deckLearnCardAnalysisWidget, binding.deckDueCardAnalysisWidget)
            val name = binding.deckNameCardAnalysis
            val textViews = counts + name
            val textSizes = textViews.map { it.textSize }
            val paddedViews = listOf(binding.cardAnalysisDataHolder, name)
            val paddings = paddedViews.map { it.paddings }

            fun fitsAtScale(scale: Float): Boolean {
                textViews.zip(textSizes).forEach { (view, textSize) ->
                    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize * scale)
                }
                paddedViews.zip(paddings).forEach { (view, padding) -> view.setScaledPadding(padding, scale) }
                return widget.fitsHeight() && counts.all { it.lineCount == 1 } && name.breaksOnlyBetweenWords()
            }

            val shrunk = !fitsAtScale(1f)
            if (shrunk) fitsAtScale(largestFittingScale(textSizes.max()) { fitsAtScale(it) })
            textViews.forEach { copyTextSize(context, it, shrunk) }
            paddedViews.forEach { copyPadding(it) }
        }

        /**
         * Fits the message in [size] without splitting a word: first by trimming the padding,
         * then by switching to [shorterText], and only then by shrinking the text.
         *
         * @param context used to inflate and measure the layout
         * @param size the widget size in dp, unbounded where a side is 0
         * @param text shown when it fits, trimming the padding if needed
         * @param shorterText shown when [text] does not fit even without padding, shrunk if it still does not fit
         */
        private fun RemoteViews.fitMessage(
            context: Context,
            size: SizeF,
            text: String,
            shorterText: String,
        ) {
            val widget = OffscreenWidget(context, this, size)
            val message = WidgetCardAnalysisBinding.bind(widget.root).emptyWidget
            val textSize = message.textSize
            val padding = message.paddings

            fun fits(
                paddingScale: Float,
                textScale: Float = 1f,
            ): Boolean {
                message.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize * textScale)
                message.setScaledPadding(padding, paddingScale)
                return widget.fitsHeight() && message.breaksOnlyBetweenWords()
            }

            fun fitsWithLessPadding(candidate: String): Boolean {
                message.text = candidate
                return when {
                    fits(paddingScale = 1f) -> true
                    !fits(paddingScale = 0f) -> false
                    else -> {
                        fits(largestFittingScale(padding.max().toFloat()) { fits(paddingScale = it) })
                        true
                    }
                }
            }

            val shrunk = !fitsWithLessPadding(text) && !fitsWithLessPadding(shorterText)
            if (shrunk) fits(paddingScale = 0f, textScale = largestFittingScale(textSize) { fits(paddingScale = 0f, textScale = it) })
            copyTextSize(context, message, shrunk)
            setTextViewText(R.id.empty_widget, message.text)
            copyPadding(message)
        }

        /** Binary search for the largest scale that still fits, to within 1px of [largestSize]. */
        private inline fun largestFittingScale(
            largestSize: Float,
            fitsAtScale: (Float) -> Boolean,
        ): Float {
            var minScale = 0f
            var maxScale = 1f
            while ((maxScale - minScale) * largestSize > 1f) {
                val scale = (minScale + maxScale) / 2
                if (fitsAtScale(scale)) minScale = scale else maxScale = scale
            }
            return minScale
        }

        private fun RemoteViews.copyTextSize(
            context: Context,
            view: TextView,
            shrunk: Boolean,
        ) = if (shrunk) {
            setTextViewTextSize(view.id, TypedValue.COMPLEX_UNIT_PX, view.textSize)
        } else {
            setTextViewTextSize(
                view.id,
                TypedValue.COMPLEX_UNIT_SP,
                TypedValueCompat.pxToSp(view.textSize, context.resources.displayMetrics),
            )
        }

        private fun RemoteViews.copyPadding(view: View) =
            setViewPadding(view.id, view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)

        private val View.paddings get() = listOf(paddingLeft, paddingTop, paddingRight, paddingBottom)

        private fun View.setScaledPadding(
            padding: List<Int>,
            scale: Float,
        ) {
            val (left, top, right, bottom) = padding.map { (it * scale).roundToInt() }
            setPadding(left, top, right, bottom)
        }

        private fun TextView.breaksOnlyBetweenWords(): Boolean {
            val layout = layout ?: return true
            val lineBreaks = BreakIterator.getLineInstance()
            lineBreaks.setText(text.toString())
            return (0 until layout.lineCount - 1).all { lineBreaks.isBoundary(layout.getLineEnd(it)) }
        }

        /**
         * Updates the Card Analysis Widgets based on the current state of the application.
         * It fetches the App Widget IDs and updates each widget with the associated deck ID.
         */
        fun updateCardAnalysisWidgets(context: Context) {
            val appWidgetManager = getAppWidgetManager(context) ?: return

            val provider = ComponentName(context, CardAnalysisWidget::class.java)
            Timber.d("Fetching appWidgetIds for provider: ${provider.shortClassName}")

            val appWidgetIds = appWidgetManager.getAppWidgetIdsEx(provider)
            Timber.d("AppWidgetIds to update: ${appWidgetIds.joinToString(", ")}")

            for (appWidgetId in appWidgetIds) {
                getDeckIdForWidget(context, appWidgetId)
                updateWidget(context, appWidgetManager, appWidgetId)
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

        for (widgetId in appWidgetIds) {
            Timber.d("Updating widget with ID: $widgetId")

            // Get the selected deck ID internally
            val selectedDeckId = getDeckIdForWidget(context, widgetId)

            /*
             * Explanation of behavior when selectedDeckId is empty
             * If selectedDeckId is empty, the widget will retain the previous deck.
             * This behavior ensures that the widget does not display an empty view, which could be
             * confusing to the user. Instead, it maintains the last known state until a new valid
             * deck ID is provided. This approach prioritizes providing a consistent
             * user experience over showing an empty or default state.
             */
            Timber.d("Selected deck ID: $selectedDeckId for widget ID: $widgetId")

            // Update the widget with the selected deck ID
            updateWidget(context, appWidgetManager, widgetId)
            // Set the recurring alarm for the widget
            setRecurringAlarm(context, widgetId, CardAnalysisWidget::class.java)
        }

        Timber.d("Widget update process completed for appWidgetIds: ${appWidgetIds.joinToString(", ")}")
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val appWidgetManager = getAppWidgetManager(context) ?: return
        super.onReceive(context, intent)

        val widgetPreferences = CardAnalysisWidgetPreferences(context)

        when (intent.action) {
            ACTION_APPWIDGET_UPDATE -> {
                // Retrieve the widget ID from the intent
                val appWidgetId = intent.getAppWidgetId()
                val selectedDeckId = intent.getLongExtra(EXTRA_SELECTED_DECK_ID, -1L)

                Timber.d("Received ACTION_APPWIDGET_UPDATE with widget ID: $appWidgetId and selectedDeckId: $selectedDeckId")

                if (appWidgetId != INVALID_APPWIDGET_ID) {
                    Timber.d("Updating widget with ID: $appWidgetId")

                    // Update the widget using the internally fetched deck ID
                    updateWidget(context, appWidgetManager, appWidgetId)

                    Timber.d("Widget update process completed for widget ID: $appWidgetId")
                }
            }
            // Custom action to update a specific widget, triggered by the setRecurringAlarm method
            ACTION_UPDATE_WIDGET -> {
                val appWidgetId = intent.getAppWidgetId()
                if (appWidgetId != INVALID_APPWIDGET_ID) {
                    Timber.d("Received ACTION_UPDATE_WIDGET for widget ID: $appWidgetId")

                    // Update the widget using the internally fetched deck ID
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
            }
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED -> {
                Timber.d("ACTION_APPWIDGET_OPTIONS_CHANGED received from CardAnalysisWidget")
            }
            AppWidgetManager.ACTION_APPWIDGET_DELETED -> {
                Timber.d("ACTION_APPWIDGET_DELETED received")
                val appWidgetId = intent.getAppWidgetId()
                if (appWidgetId != INVALID_APPWIDGET_ID) {
                    Timber.d("Deleting widget with ID: $appWidgetId")
                    cancelRecurringAlarm(context, appWidgetId, CardAnalysisWidget::class.java)
                    widgetPreferences.deleteDeckData(appWidgetId)
                } else {
                    Timber.e("Invalid widget ID received in ACTION_APPWIDGET_DELETED")
                }
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
                    "CardAnalysisWidget - onReceive",
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

        val widgetPreferences = CardAnalysisWidgetPreferences(context)

        AppWidgetIds.of(appWidgetIds)?.forEach { appWidgetId ->
            cancelRecurringAlarm(context, appWidgetId, CardAnalysisWidget::class.java)
            widgetPreferences.deleteDeckData(appWidgetId)
        }
    }
}

private class OffscreenWidget(
    context: Context,
    views: RemoteViews,
    size: SizeF,
) {
    private val density = context.resources.displayMetrics.density
    private val widthSpec =
        MeasureSpec.makeMeasureSpec(
            (size.width * density).roundToInt(),
            if (size.width > 0) MeasureSpec.EXACTLY else MeasureSpec.UNSPECIFIED,
        )
    private val availableHeight = if (size.height > 0) size.height * density else Float.POSITIVE_INFINITY
    val root: View = views.apply(context, null)

    fun fitsHeight(): Boolean {
        root.measure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        return root.measuredHeight <= availableHeight
    }
}
