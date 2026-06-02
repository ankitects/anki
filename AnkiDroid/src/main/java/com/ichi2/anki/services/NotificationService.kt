/*
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
import com.ichi2.anki.Channel
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.R
import com.ichi2.anki.android.AnkiBroadcastReceiver
import com.ichi2.anki.canUserAccessDeck
import com.ichi2.anki.common.annotations.LegacyNotifications
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.libanki.EpochMilliseconds
import com.ichi2.anki.preferences.PENDING_NOTIFICATIONS_ONLY
import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.reviewreminders.upsertReminder
import com.ichi2.anki.runGloballyWithTimeout
import com.ichi2.anki.services.NotificationService.Companion.addAction
import com.ichi2.anki.services.NotificationService.Companion.getIntent
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.ext.allDecksCounts
import com.ichi2.anki.utils.ext.getParcelableCompat
import com.ichi2.anki.utils.remainingTime
import com.ichi2.widget.WidgetStatus
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Performs the actual firing of review reminder notifications, both recurring ones and snoozed ones.
 * See [ReviewReminder] for the distinction between a "review reminder" and a "notification".
 * The scheduling of these notifications is handled by [AlarmManagerService].
 *
 * This service can be triggered in one of two possible ways, depending on whether the notification
 * being fired is a recurring notification or a one-time snoozed notification. See [NotificationServiceAction].
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
         * Extra key for sending a review reminder as an extra to this broadcast receiver.
         */
        @VisibleForTesting
        const val EXTRA_REVIEW_REMINDER = "notification_service_review_reminder"

        /**
         * Timeout for the process of sending a review reminder notification.
         * Should be below 10 seconds as BroadcastReceivers may ANR when onReceiveBroadcast takes longer than 10 seconds.
         * See [the docs](https://developer.android.com/reference/android/content/BroadcastReceiver#goAsync()).
         */
        private val SEND_REVIEW_REMINDER_TIMEOUT = 8.seconds

        /**
         * Triggered by [onReceiveBroadcast]. Does some bookkeeping if the triggered notification is of the recurring type
         * or does nothing if it is a snoozed one, and then begins the process of sending the notification.
         */
        @VisibleForTesting
        suspend fun handleReviewReminderNotification(
            context: Context,
            reviewReminder: ReviewReminder,
            isRecurringNotification: Boolean,
        ) {
            if (isRecurringNotification) {
                // Record this latest routine notification-firing attempt's timestamp
                reviewReminder.updateLatestNotifTime()
                when (val scope = reviewReminder.scope) {
                    is ReviewReminderScope.DeckSpecific ->
                        ReviewRemindersDatabase.editRemindersForDeck(
                            scope.did,
                            upsertReminder(reviewReminder),
                        )
                    is ReviewReminderScope.Global -> ReviewRemindersDatabase.editAllAppWideReminders(upsertReminder(reviewReminder))
                }

                // Schedule the next routine notification-firing
                Timber.d("Scheduling next review reminder notification")
                AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminder)
            }

            // Send the notification
            try {
                sendReviewReminderNotification(context, reviewReminder)
            } catch (e: BackendException) {
                // This may occur if the collection is blocked, in which case we should fail gracefully
                Timber.w(e, "Aborted review reminder notification due to backend exception")
            } catch (e: CancellationException) {
                Timber.w(e, "Aborted review reminder notification due to cancellation exception")
                throw e // Rethrow to propagate to parent coroutine
            }
        }

        /**
         * Sends a notification for a review reminder.
         */
        private suspend fun sendReviewReminderNotification(
            context: Context,
            reviewReminder: ReviewReminder,
        ) {
            Timber.i("sendReviewReminderNotification for ${reviewReminder.id}")
            Timber.v("Review reminder: $reviewReminder")

            if (reviewReminder.scope is ReviewReminderScope.DeckSpecific) {
                val isDeckAccessible = canUserAccessDeck(reviewReminder.scope.did)
                if (!isDeckAccessible) {
                    Timber.i("Deck with ID ${reviewReminder.scope.did} not found, aborting")
                    return
                }
            }

            // Cancel if the user wants notifications to only fire if no reviews have been done today AND there has been a review today
            if (reviewReminder.onlyNotifyIfNoReviews && wasScopeReviewedToday(reviewReminder.scope)) {
                Timber.i("Aborting notification due to onlyNotifyIfNoReviews")
                return
            }

            val dueCardsCount =
                when (reviewReminder.scope) {
                    is ReviewReminderScope.Global -> withCol { sched.allDecksCounts() }
                    is ReviewReminderScope.DeckSpecific ->
                        withCol {
                            decks.select(reviewReminder.scope.did)
                            sched.counts()
                        }
                }
            val dueCardsTotal = dueCardsCount.count()
            if (dueCardsTotal < reviewReminder.cardTriggerThreshold.threshold) {
                Timber.i("Aborting notification due to threshold: $dueCardsTotal < ${reviewReminder.cardTriggerThreshold.threshold}")
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
                    is ReviewReminderScope.Global -> "It's time to study your cards"
                    is ReviewReminderScope.DeckSpecific -> {
                        val fullDeckName = reviewReminder.scope.getDeckName()
                        val deckName =
                            Decks.basename(fullDeckName) // don't show the full path with "::" included
                        "It's time to study $deckName"
                    }
                }

            val eta = withCol { sched.eta(dueCardsCount, false) }
            val remainingTimeString = remainingTime(context, (eta * 60).toLong())
            val description = "$dueCardsTotal cards due, $remainingTimeString"

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
            val fiveMinuteSnooze = createSnoozePendingIntent(context, reviewReminder, 5.minutes)
            val oneHourSnooze = createSnoozePendingIntent(context, reviewReminder, 1.hours)

            val builder =
                NotificationCompat
                    .Builder(context, Channel.REVIEW_REMINDERS.id)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setSmallIcon(R.drawable.ic_star_notify)
                    .setColor(context.getColor(R.color.material_light_blue_700))
                    .setContentTitle(title)
                    .setContentText(description)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true) // Dismiss on click
                    .setTicker(title) // Accessibility
                    .addAction("Snooze 5m", fiveMinuteSnooze)
                    .addAction("Snooze 1h", oneHourSnooze)
                    // Vibration and priority are set here for backwards compatibility; they are set via channel for API 33+
                    .setVibrate(longArrayOf(0, 500))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            val manager = context.getSystemService<NotificationManager>()
            if (manager != null) {
                Timber.i("Sending notification with ID ${reviewReminder.id.value}")
                manager.notify(REVIEW_REMINDER_NOTIFICATION_TAG, reviewReminder.id.value, builder.build())
            } else {
                Timber.w("Failed to get NotificationManager system service, aborting review reminder notification")
            }
        }

        /**
         * Creates review reminder snoozing pending intent for a given review reminder and snooze interval.
         * If this method is run twice for the same review reminder ID and snooze interval, it will return the same
         * pending intent.
         */
        private fun createSnoozePendingIntent(
            context: Context,
            reviewReminder: ReviewReminder,
            snoozeInterval: Duration,
        ): PendingIntent? {
            val intent =
                AlarmManagerService.getIntent(
                    context,
                    reviewReminder,
                    snoozeInterval,
                )
            Timber.v("Created snooze intent with action ${intent.action}")
            return PendingIntentCompat.getBroadcast(
                context,
                reviewReminder.id.value,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT,
                false,
            )
        }

        /**
         * Checks if a deck, or any decks, have been reviewed since the latest day cutoff.
         * Used for the "only notify me if no reviews have been done today" review reminder feature.
         */
        private suspend fun wasScopeReviewedToday(scope: ReviewReminderScope): Boolean {
            // Handles the global and deck-specific scope cases separately to avoid the need for string concatenation,
            // thus protecting against SQL injection. Checks for existence rather than counting to increase efficiency.
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
                            // Essentially the same as above, but only check through cards with a deck ID matching the provided scope
                            val query = """
                        SELECT EXISTS (
                            SELECT 1
                            FROM cards
                            JOIN revlog ON revlog.cid = cards.id
                            WHERE revlog.id > ?
                            AND cards.did = ?
                        )
                    """
                            db.queryScalar(query, startOfToday, scope.did)
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
                        context.getString(R.string.pref_notifications_minimum_cards_due_key),
                        PENDING_NOTIFICATIONS_ONLY.toString(),
                    )!!
                    .toInt()
            val dueCardsCount = WidgetStatus.fetchDue(context)
            if (dueCardsCount >= minCardsDue) {
                // Build basic notification
                val cardsDueText =
                    context.resources
                        .getQuantityString(
                            R.plurals.widget_minimum_cards_due_notification_ticker_text,
                            dueCardsCount,
                            dueCardsCount,
                        )
                // This generates a log warning "Use of stream types is deprecated..."
                // The NotificationCompat code uses setSound() no matter what we do and triggers it.
                val builder =
                    NotificationCompat
                        .Builder(
                            context,
                            Channel.GENERAL.id,
                        ).setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setSmallIcon(R.drawable.ic_star_notify)
                        .setColor(context.getColor(R.color.material_light_blue_700))
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
         * When broadcasted, fires a notification for the provided review reminder.
         *
         * @param context
         * @param reviewReminder
         * @param intentAction If this is [NotificationServiceAction.ScheduleRecurringNotifications],
         * this intent (once fired) will also schedule the next upcoming instance of the review reminder
         * notification via [AlarmManagerService.scheduleReviewReminderNotification].
         *
         * @see NotificationServiceAction
         */
        fun getIntent(
            context: Context,
            reviewReminder: ReviewReminder,
            intentAction: NotificationServiceAction,
        ) = Intent(context, NotificationService::class.java).apply {
            action = intentAction.actionString
            putExtra(EXTRA_REVIEW_REMINDER, reviewReminder)
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
     * then we can be sure that the next instance has already been scheduled when the user initially
     * pressed snooze.
     *
     * @see AlarmManagerService.getReviewReminderNotificationPendingIntent
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
            Timber.d("onReceiveBroadcast")
            val action = intent.action ?: return
            val extras = intent.extras ?: return
            val reviewReminder =
                try {
                    extras.getParcelableCompat<ReviewReminder>(EXTRA_REVIEW_REMINDER) ?: return
                } catch (e: BadParcelableException) {
                    // #20782: If the extra's review reminder has an invalid schema, attempt a full re-schedule of all
                    // review reminder notifications, as that will retrieve reminders from Shared Preferences
                    // and handle any necessary schema updates. It will also trigger immediate notifications
                    // (and thus this onReceiveBroadcast again for an uncorrupted version of this corrupted review reminder) as necessary.
                    Timber.w(e, "Failed to get review reminder from intent extras, triggering full re-schedule")
                    AlarmManagerService.scheduleAllEnabledReviewReminderNotifications(context)
                    return
                }
            Timber.d("onReceiveBroadcast: ${reviewReminder.id}")

            // We must run some suspending functions. Hence we mark this onReceiveBroadcast function as long-running
            // and use the global scope for simplicity's sake. Theoretically, we could also use an expedited Worker,
            // but AnkiDroid is only allotted a fixed number of expedited Worker calls per day
            // and those expedited calls are also used by the sync service, so it's best to conserve them.
            runGloballyWithTimeout(SEND_REVIEW_REMINDER_TIMEOUT) {
                handleReviewReminderNotification(
                    context,
                    reviewReminder,
                    isRecurringNotification = (action == NotificationServiceAction.ScheduleRecurringNotifications.actionString),
                )
            }
        } else {
            triggerNotificationFor(context)
        }
    }
}
