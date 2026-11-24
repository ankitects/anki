// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.BadParcelableException
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.getSystemService
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonPreferenceKey
import com.ichi2.anki.CommonString
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.NotificationChannel
import com.ichi2.anki.R
import com.ichi2.anki.canUserAccessDeck
import com.ichi2.anki.common.android.AnkiBroadcastReceiver
import com.ichi2.anki.common.annotations.LegacyNotifications
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.ext.allDecksCounts
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.libanki.EpochMilliseconds
import com.ichi2.anki.libanki.Utils
import com.ichi2.anki.libanki.sched.Counts
import com.ichi2.anki.preferences.PENDING_NOTIFICATIONS_ONLY
import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewReminderAlarmManager
import com.ichi2.anki.reviewreminders.ReviewReminderId
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.reviewreminders.reminderLogPrefix
import com.ichi2.anki.runGloballyWithTimeout
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.ext.getParcelableCompat
import com.ichi2.utils.LanguageUtil.withUnicodeIsolation
import com.ichi2.widget.WidgetStatus
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import com.ichi2.anki.common.android.R as CommonR

/**
 * Performs the actual firing of review reminder notifications, both recurring ones and snoozed ones.
 * See [ReviewReminder] for the distinction between a "review reminder" and a "notification".
 * The scheduling of these notifications is handled by [ReviewReminderAlarmManager].
 *
 * This service can be triggered in one of two possible ways, depending on whether the notification
 * being fired is a recurring notification or a one-time snoozed notification. See [NotificationServiceAction].
 *
 * TODO: Rename and reorganize the (handle/send/fire)ReviewReminderNotification functions for better readability and separation of concerns.
 * These three verbs are way too similar. The "send" method should be split into "validate" and "hydrate". "send" sometimes aborts and does nothing.
 * The distinction between "handle" and "send" should be made more clear. All uses of "fire" in the codebase, including in documentation,
 * should be aligned to mean "trigger the OS display unconditionally."
 */
class NotificationService : AnkiBroadcastReceiver() {
    companion object {
        /**
         * NotificationManager tag for review reminder notifications, passed to the [NotificationManager.notify] method.
         * We specify this explicitly so that even if other parts of AnkiDroid create a notification with the
         * same integer ID as a review reminder notification (see [com.ichi2.anki.notifications.NotificationId]),
         * the two can coexist simultaneously without one interfering with the other.
         */
        const val REVIEW_REMINDER_NOTIFICATION_TAG = "com.ichi2.anki.review_reminder_notification_tag"

        /**
         * Extra key for sending a [ReviewReminderId] as an extra to this broadcast receiver.
         */
        @VisibleForTesting
        const val EXTRA_REVIEW_REMINDER_ID = "notification_service_review_reminder_id"

        /**
         * Extra key for sending a [ReviewReminderScope] as an extra to this broadcast receiver.
         */
        @VisibleForTesting
        const val EXTRA_REVIEW_REMINDER_SCOPE = "notification_service_review_reminder_scope"

        /**
         * Timeout for the process of sending a review reminder notification.
         */
        private val SEND_REVIEW_REMINDER_TIMEOUT = 8.seconds

        /**
         * Triggered by [onReceiveBroadcast]. Retrieves the review reminder associated with the provided ID.
         * Schedules the next notification and updates the latest firing time if the triggered notification is of the recurring type
         * or does nothing if it is a snoozed one, and then begins the process of sending the notification.
         *
         * Extracted from the body of [onReceiveBroadcast] to allow for easier testing and to keep
         * the receiver method concise.
         *
         * @param context
         * @param reviewReminderId The ID of the review reminder the notification is for.
         * @param reviewReminderScope The scope that the review reminder ID is stored within.
         * @param isRecurringNotification If true, the fetched review reminder does not have its next
         * firing scheduled, nor is its latest firing time updated.
         */
        @VisibleForTesting
        suspend fun handleReviewReminderNotification(
            context: Context,
            reviewReminderId: ReviewReminderId,
            reviewReminderScope: ReviewReminderScope,
            isRecurringNotification: Boolean,
        ) {
            val reminderForNotification =
                if (isRecurringNotification) {
                    ReviewRemindersDatabase.retrieveRefreshedReminder(
                        reviewReminderId,
                        reviewReminderScope,
                    )
                } else {
                    ReviewRemindersDatabase.getRemindersForScope(reviewReminderScope)[reviewReminderId]
                }
            if (reminderForNotification == null) {
                Timber.i(
                    "${reminderLogPrefix(reviewReminderId)} skip: Aborting notification for review reminder " +
                        "${reviewReminderId.value} (recurring: $isRecurringNotification) due to database validation failure.",
                )
                return
            }
            if (!reminderForNotification.enabled) {
                // This should never happen for recurring notifications because disabling a reminder should cancel all of its notification alarms, but we check just in case
                // It can happen for snoozed notifications if the user disables the reminder after snoozing it
                Timber.i(
                    "${reminderLogPrefix(reviewReminderId)} skip: Aborting notification for review reminder " +
                        "${reviewReminderId.value} (recurring: $isRecurringNotification) because the reminder is disabled.",
                )
                return
            }

            if (isRecurringNotification) {
                Timber.d("Scheduling next review reminder notification for review reminder $reviewReminderId")
                // Because this scheduling is already the result of a notification, we do not trigger an immediate notification
                ReviewReminderAlarmManager.scheduleReviewReminderNotification(
                    context,
                    reminderForNotification,
                    attemptImmediateNotification = false,
                )
            }

            // Send the notification
            try {
                sendReviewReminderNotification(context, reminderForNotification)
            } catch (e: BackendException) {
                // This may occur if the collection is blocked, in which case we should fail gracefully
                Timber.w(e, "${reminderLogPrefix(reviewReminderId)} Aborted review reminder notification due to backend exception")
            } catch (e: CancellationException) {
                Timber.w(
                    e,
                    "${reminderLogPrefix(reviewReminderId)} Aborted review reminder notification due to cancellation exception",
                )
                throw e // Rethrow to propagate to parent coroutine
            }
        }

        /**
         * (Attempts to) send a notification for a review reminder.
         * Specifically, performs validation and content + description hydration, then triggers the firing of the notification.
         * May abort sending the notification depending on certain fields of the review reminder.
         *
         * Does not handle scheduling the next occurrence of the notification if the review reminder is recurring; that is handled by [handleReviewReminderNotification].
         * Does not handle big-picture validation of the review reminder (i.e. is it in the database, is it enabled);
         * that is handled by [handleReviewReminderNotification].
         */
        private suspend fun sendReviewReminderNotification(
            context: Context,
            reviewReminder: ReviewReminder,
        ) {
            Timber.i("${reminderLogPrefix(reviewReminder.id)} sendReviewReminderNotification for ${reviewReminder.id.value}")
            Timber.v("Review reminder: $reviewReminder")

            if (reviewReminder.scope is ReviewReminderScope.DeckSpecific) {
                val isDeckAccessible = canUserAccessDeck(reviewReminder.scope.did)
                if (!isDeckAccessible) {
                    Timber.i("${reminderLogPrefix(reviewReminder.id)} skip: Deck with ID ${reviewReminder.scope.did} not found, aborting")
                    return
                }
            }

            // Cancel if the user wants notifications to only fire if no reviews have been done today AND there has been a review today
            if (reviewReminder.onlyNotifyIfNoReviews && wasScopeReviewedToday(reviewReminder.scope)) {
                Timber.i("${reminderLogPrefix(reviewReminder.id)} skip: Aborting notification due to onlyNotifyIfNoReviews")
                return
            }

            val dueCardsCount =
                when (reviewReminder.scope) {
                    is ReviewReminderScope.Global -> withCol { sched.allDecksCounts() }
                    is ReviewReminderScope.DeckSpecific -> {
                        val deckNode = withCol { sched.deckDueTree().find(reviewReminder.scope.did) }
                        if (deckNode == null) {
                            Timber.e(
                                "${reminderLogPrefix(reviewReminder.id)} Aborting notification: " +
                                    "deck ${reviewReminder.scope.did} is not in the deck tree",
                            )
                            return
                        }
                        Counts(new = deckNode.newCount, lrn = deckNode.lrnCount, rev = deckNode.revCount)
                    }
                }
            val dueCardsTotal = dueCardsCount.count()
            val consideredCardsCount = reviewReminder.thresholdFilter.filterCounts(dueCardsCount)
            val consideredCardsTotal = consideredCardsCount.count()

            if (consideredCardsTotal < reviewReminder.cardTriggerThreshold.threshold) {
                Timber.i(
                    "${reminderLogPrefix(reviewReminder.id)} skip: Aborting notification due to cardTriggerThreshold " +
                        "(due: $dueCardsCount, considered: $consideredCardsCount, threshold: ${reviewReminder.cardTriggerThreshold.threshold})",
                )
                return
            }

            val onClickIntent =
                when (reviewReminder.scope) {
                    is ReviewReminderScope.Global -> Intent(context, DeckPicker::class.java)
                    is ReviewReminderScope.DeckSpecific -> {
                        val deckId = reviewReminder.scope.did
                        IntentHandler.getReviewDeckIntent(context, deckId)
                    }
                }
            onClickIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            val title =
                when (reviewReminder.scope) {
                    is ReviewReminderScope.Global -> context.getString(CommonString.review_reminder_notification_title_all_decks)
                    is ReviewReminderScope.DeckSpecific -> {
                        val fullDeckName = reviewReminder.scope.getDeckName()
                        val deckName =
                            Decks.basename(fullDeckName) // don't show the full path with "::" included
                        context.getString(
                            CommonString.review_reminder_notification_title_deck,
                            withUnicodeIsolation(deckName),
                        )
                    }
                }
            val description =
                context.resources.getQuantityString(
                    CommonPlurals.review_reminder_notification_cards_due,
                    dueCardsTotal,
                    dueCardsTotal,
                )

            fireReviewReminderNotification(context, reviewReminder, title, description, onClickIntent)
        }

        /**
         * Fires a notification with the given title, description, and intent for click actions.
         * Requires the review reminder the notification is for in order to get a unique ID and create
         * the hardcoded snooze buttons.
         */
        private fun fireReviewReminderNotification(
            context: Context,
            reviewReminder: ReviewReminder,
            title: String,
            description: String,
            onClickIntent: Intent,
        ) {
            val pendingIntent =
                PendingIntentCompat
                    .getActivity(
                        context,
                        reviewReminder.id.value,
                        onClickIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT,
                        false,
                    )

            // Create intents for snooze buttons
            val fiveMinuteSnooze = SnoozeService.getPendingIntent(context, reviewReminder.id, reviewReminder.scope, 5.minutes)
            val oneHourSnooze = SnoozeService.getPendingIntent(context, reviewReminder.id, reviewReminder.scope, 1.hours)

            val builder =
                NotificationCompat
                    .Builder(context, NotificationChannel.REVIEW_REMINDERS.id)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setSmallIcon(R.drawable.ic_star_notify)
                    .setColor(context.getColor(CommonR.color.material_light_blue_700))
                    .setContentTitle(title)
                    .setContentText(description)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true) // Dismiss on click
                    .setTicker(title) // Accessibility
                    .addAction(
                        context.getString(CommonString.review_reminder_notification_snooze_button, 5.minutes.toString()),
                        fiveMinuteSnooze,
                    ).addAction(
                        context.getString(CommonString.review_reminder_notification_snooze_button, 1.hours.toString()),
                        oneHourSnooze,
                    )
                    // Vibration and priority are set here for backwards compatibility; they are set via channel for API 33+
                    .setVibrate(longArrayOf(0, 500))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            val manager = context.getSystemService<NotificationManager>()
            if (manager != null) {
                Timber.i("${reminderLogPrefix(reviewReminder.id)} Firing notification with ID ${reviewReminder.id.value}")
                manager.notify(REVIEW_REMINDER_NOTIFICATION_TAG, reviewReminder.id.value, builder.build())
            } else {
                Timber.w(
                    "${reminderLogPrefix(reviewReminder.id)} Failed to get NotificationManager system service, " +
                        "aborting review reminder notification",
                )
            }
        }

        /**
         * Checks if a deck, or any decks, have been reviewed since the latest day cutoff.
         * Used for the "only notify me if no reviews have been done today" review reminder feature.
         */
        private suspend fun wasScopeReviewedToday(scope: ReviewReminderScope): Boolean {
            // Handles the global and deck-specific scope cases separately.
            // Checks for existence rather than counting to increase efficiency.
            val queryResult =
                when (scope) {
                    is ReviewReminderScope.Global ->
                        withCol {
                            val startOfToday: EpochMilliseconds = sched.dayCutoff * 1000 - 1.days.inWholeMilliseconds
                            // For each card in the user's collection, retrieve and JOIN information about its review history
                            // Then check if there exists at least one card with a review log entry after the start of today
                            val query = """
                        SELECT EXISTS (
                            SELECT 1
                            FROM cards
                            JOIN revlog ON revlog.cid = cards.id
                            WHERE revlog.id > ?
                        )
                    """
                            db.queryScalar(query, startOfToday)
                        }
                    is ReviewReminderScope.DeckSpecific ->
                        withCol {
                            val startOfToday: EpochMilliseconds = sched.dayCutoff * 1000 - 1.days.inWholeMilliseconds
                            // Essentially the same as above, but only check through cards in the provided deck or its subdecks,
                            // including cards temporarily moved into a filtered deck (whose home deck is stored in odid)
                            // Deck IDs are numbers, so building the IN list from them is safe
                            val deckIds = Utils.ids2str(decks.deckAndChildIds(scope.did))
                            val query = """
                        SELECT EXISTS (
                            SELECT 1
                            FROM cards
                            JOIN revlog ON revlog.cid = cards.id
                            WHERE revlog.id > ?
                            AND (cards.did IN $deckIds OR cards.odid IN $deckIds)
                        )
                    """
                            db.queryScalar(query, startOfToday)
                        }
                }
            return (queryResult == 1)
        }

        /** The id of the notification for due cards.  */
        @LegacyNotifications("Each notification will have a unique ID")
        private const val WIDGET_NOTIFY_ID = 1

        @LegacyNotifications("Replaced by new review reminder notification firing logic")
        fun triggerNotificationFor(context: Context) {
            Timber.i("NotificationService: OnStartCommand")
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val preferences = context.sharedPrefs()
            val minCardsDue =
                preferences
                    .getString(
                        context.getString(CommonPreferenceKey.pref_notifications_minimum_cards_due_key),
                        PENDING_NOTIFICATIONS_ONLY.toString(),
                    )!!
                    .toInt()
            val dueCardsCount = WidgetStatus.fetchDue()
            if (dueCardsCount >= minCardsDue) {
                // Build basic notification
                val cardsDueText =
                    context.resources
                        .getQuantityString(
                            CommonPlurals.widget_minimum_cards_due_notification_ticker_text,
                            dueCardsCount,
                            dueCardsCount,
                        )
                // This generates a log warning "Use of stream types is deprecated..."
                // The NotificationCompat code uses setSound() no matter what we do and triggers it.
                val builder =
                    NotificationCompat
                        .Builder(
                            context,
                            NotificationChannel.GENERAL.id,
                        ).setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setSmallIcon(R.drawable.ic_star_notify)
                        .setColor(context.getColor(CommonR.color.material_light_blue_700))
                        .setContentTitle(cardsDueText)
                        .setTicker(cardsDueText)
                // Enable vibrate and blink if set in preferences
                if (preferences.getBoolean("widgetVibrate", false)) {
                    builder.setVibrate(longArrayOf(1000, 1000, 1000))
                }
                if (preferences.getBoolean("widgetBlink", false)) {
                    builder.setLights(Color.BLUE, 1000, 1000)
                }
                // Creates an explicit intent for an Activity in your app
                val resultIntent = Intent(context, DeckPicker::class.java)
                resultIntent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                val resultPendingIntent =
                    PendingIntentCompat.getActivity(
                        context,
                        0,
                        resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT,
                        false,
                    )
                builder.setContentIntent(resultPendingIntent)
                // mId allows you to update the notification later on.
                manager.notify(WIDGET_NOTIFY_ID, builder.build())
            } else {
                // Cancel the existing notification, if any.
                manager.cancel(WIDGET_NOTIFY_ID)
            }
        }

        /**
         * A replacement for the default [NotificationCompat.Builder.addAction].
         * The default requires an icon for the action button, but the icon only actually shows up below API 24,
         * which is less than AnkiDroid's minimum supported API level.
         * Hence, this helper automatically inserts a dummy icon.
         */
        private fun NotificationCompat.Builder.addAction(
            title: String,
            intent: PendingIntent?,
        ): NotificationCompat.Builder = addAction(R.drawable.ic_fast_forward, title, intent)

        /**
         * Method for getting an intent for this service.
         * When broadcasted, fires a notification for the review reminder associated with the provided review reminder ID.
         *
         * @param context
         * @param reviewReminderId
         * @param reviewReminderScope Scope to search for the review reminder ID in.
         * @param intentAction If this is [NotificationServiceAction.ScheduleRecurringNotifications],
         * this intent (once fired) will also schedule the next upcoming instance of the review reminder
         * notification via [ReviewReminderAlarmManager.scheduleReviewReminderNotification].
         *
         * @see NotificationServiceAction
         */
        fun getIntent(
            context: Context,
            reviewReminderId: ReviewReminderId,
            reviewReminderScope: ReviewReminderScope,
            intentAction: NotificationServiceAction,
        ) = Intent(context, NotificationService::class.java).apply {
            action = intentAction.actionString
            putExtra(EXTRA_REVIEW_REMINDER_ID, reviewReminderId)
            putExtra(EXTRA_REVIEW_REMINDER_SCOPE, reviewReminderScope)
        }
    }

    /**
     * Alarms for triggering this service's functionality are set by both the normal review reminder
     * daily repeating notifications and the one-time snoozed notifications that can be created by clicking
     * snooze on a review reminder's notification. We need to explicitly distinguish between the two on the intents
     * for launching this service, otherwise the act of snoozing a notification will cancel the normal
     * daily repeating notifications.
     *
     * Additionally, when this service is directed to fire a notification, we can check if the intent action
     * is [ScheduleRecurringNotifications] to determine whether we should also schedule the next upcoming
     * instance of the review reminder notification. If the intent is instead [SnoozeNotification],
     * then we can be sure that the next instance has already been scheduled.
     *
     * @see ReviewReminderAlarmManager.getReviewReminderNotificationPendingIntent
     * @see onReceiveBroadcast
     */
    sealed class NotificationServiceAction(
        val actionString: String,
    ) {
        /**
         * Action sent to [NotificationService] when firing recurring notifications for a review reminder.
         */
        object ScheduleRecurringNotifications :
            NotificationServiceAction(actionString = "com.ichi2.anki.ACTION_SCHEDULE_REMINDER_NOTIFICATIONS")

        /**
         * Action sent to [NotificationService] when firing a one-time notification for a snoozed review reminder.
         */
        object SnoozeNotification :
            NotificationServiceAction(actionString = "com.ichi2.anki.ACTION_SNOOZE_REMINDER_NOTIFICATION")
    }

    /**
     * @see getIntent
     */
    override fun onReceiveBroadcast(
        context: Context,
        intent: Intent,
    ) {
        if (Prefs.newReviewRemindersEnabled) {
            Timber.i("${reminderLogPrefix()} NotificationService.onReceiveBroadcast")
            val action = intent.action ?: return
            val extras = intent.extras ?: return

            val reviewReminderId =
                rescheduleIfCorrupt(context) {
                    extras.getParcelableCompat<ReviewReminderId>(EXTRA_REVIEW_REMINDER_ID)
                } ?: return
            val reviewReminderScope =
                rescheduleIfCorrupt(context, idForLogging = reviewReminderId) {
                    extras.getParcelableCompat<ReviewReminderScope>(EXTRA_REVIEW_REMINDER_SCOPE)
                } ?: return
            Timber.d("onReceiveBroadcast: reminder: $reviewReminderId, scope: $reviewReminderScope, action: $action")

            runGloballyWithTimeout(SEND_REVIEW_REMINDER_TIMEOUT) {
                handleReviewReminderNotification(
                    context,
                    reviewReminderId,
                    reviewReminderScope,
                    isRecurringNotification = (action == NotificationServiceAction.ScheduleRecurringNotifications.actionString),
                )
            }
        } else {
            triggerNotificationFor(context)
        }
    }

    /**
     * Runs [read], which reads a value from the extras of a received intent. If the extras are corrupt,
     * logs the failure, re-schedules all review reminders, and returns null.
     *
     * #23084: The parceled extras of an alarm scheduled by a previous version of the app can
     * reference class names which no longer exist or are no longer Parcelable (ex. R8 renamed them).
     * Re-scheduling all review reminders replaces the corrupted intents.
     *
     * @param idForLogging The ID of the review reminder, if already known, to include in the log.
     */
    private inline fun <T : Any> rescheduleIfCorrupt(
        context: Context,
        idForLogging: ReviewReminderId? = null,
        read: () -> T?,
    ): T? =
        try {
            read()
        } catch (e: BadParcelableException) {
            Timber.w(
                e,
                "${reminderLogPrefix(idForLogging)} Failed to get review reminder ID or scope from intent extras, " +
                    "triggering full re-schedule",
            )
            runGloballyWithTimeout(SEND_REVIEW_REMINDER_TIMEOUT) {
                ReviewReminderAlarmManager.scheduleAllEnabledReviewReminderNotifications(context)
            }
            null
        }
}
