// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import android.app.Application
import android.content.Context
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.preferences.NotificationPreferences
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.android.SdCard
import com.ichi2.anki.common.utils.ext.allDecksCounts
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The status of the widget.
 */
object WidgetStatus {
    private lateinit var notificationPreferences: NotificationPreferences
    private lateinit var updateWidget: (Context) -> Unit

    /** Registers notification preferences and the small widget UI updater during app startup. */
    context(_: Application)
    fun register(
        preferences: NotificationPreferences,
        updater: (Context) -> Unit,
    ) {
        notificationPreferences = preferences
        updateWidget = updater
    }

    private var smallWidgetEnabled = false
    private var smallWidgetUpdateJob: Job? = null

    /**
     * Request the widget to update its status.
     * TODO Mike - we can reduce battery usage by widget users by removing updatePeriodMillis from metadata
     *             and replacing it with an alarm we set so device doesn't wake to update the widget, see:
     *             https://developer.android.com/guide/topics/appwidgets/#MetaData
     */
    fun updateInBackground(context: Context) {
        val preferences = context.sharedPrefs()
        smallWidgetEnabled = preferences.getBoolean("widgetSmallEnabled", false)
        val canExecuteTask = smallWidgetUpdateJob == null || smallWidgetUpdateJob?.isActive == false

        if (notificationPreferences.newReviewRemindersEnabled) {
            if (smallWidgetEnabled && canExecuteTask) {
                Timber.d("WidgetStatus.update(): updating")
                smallWidgetUpdateJob = launchSmallWidgetUpdateJob(context)
            } else {
                Timber.d("WidgetStatus.update(): already running or not enabled")
            }
        } else {
            val notificationEnabled = notificationPreferences.legacyNotificationsEnabled
            if ((smallWidgetEnabled || notificationEnabled) && canExecuteTask) {
                Timber.d("WidgetStatus.update(): updating")
                smallWidgetUpdateJob = launchSmallWidgetUpdateJob(context)
            } else {
                Timber.d("WidgetStatus.update(): already running or not enabled; enabled: %b", smallWidgetEnabled)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun launchSmallWidgetUpdateJob(context: Context): Job =
        GlobalScope.launch {
            try {
                updateSmallWidgetStatus(context)
                Timber.v("launchUpdateJob completed")
            } catch (exc: java.lang.Exception) {
                Timber.w(exc, "failure in widget update")
            }
        }

    suspend fun updateSmallWidgetStatus(context: Context) {
        if (!SdCard.isMounted) {
            Timber.w("updateStatus failed: no SD Card")
            return
        }
        val status = querySmallWidgetStatus()
        widgetRepository.storeSmallWidgetStatus(status)
        if (smallWidgetEnabled) {
            Timber.i("triggering small widget UI update")
            updateWidget(context)
        }
        if (!notificationPreferences.newReviewRemindersEnabled) {
            widgetNotificationScheduler.scheduleNotification()
        }
    }

    /** Returns the status of each of the decks.  */
    fun fetchSmall(): SmallWidgetStatus = widgetRepository.getWidgetSmallStatus()

    fun fetchDue(): Int = widgetRepository.dueCardsCount()

    private suspend fun querySmallWidgetStatus(): SmallWidgetStatus =
        withCol {
            val total = sched.allDecksCounts()
            val eta = sched.eta(total, false)
            SmallWidgetStatus(total.count(), eta)
        }
}
