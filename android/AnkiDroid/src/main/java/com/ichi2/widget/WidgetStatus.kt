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

package com.ichi2.widget

import android.content.Context
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.MetaDB
import com.ichi2.anki.R
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.android.SdCard
import com.ichi2.anki.common.utils.ext.allDecksCounts
import com.ichi2.anki.settings.Prefs
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * @param dueCardsCount The number of due cards (new + lrn + rev)
 * @param eta The estimated time to review
 */
data class SmallWidgetStatus(
    val dueCardsCount: Int,
    val eta: Int,
)

/**
 * The status of the widget.
 */
object WidgetStatus {
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

        if (Prefs.newReviewRemindersEnabled) {
            if (smallWidgetEnabled && canExecuteTask) {
                Timber.d("WidgetStatus.update(): updating")
                smallWidgetUpdateJob = launchSmallWidgetUpdateJob(context)
            } else {
                Timber.d("WidgetStatus.update(): already running or not enabled")
            }
        } else {
            val notificationEnabled =
                preferences
                    .getString(context.getString(R.string.pref_notifications_minimum_cards_due_key), "1000001")!!
                    .toInt() < 1000000
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
        MetaDB.storeSmallWidgetStatus(context, status)
        if (smallWidgetEnabled) {
            Timber.i("triggering small widget UI update")
            AnkiDroidWidgetSmall.UpdateService().doUpdate(context)
        }
        if (!Prefs.newReviewRemindersEnabled) {
            (context.applicationContext as AnkiDroidApp).scheduleNotification()
        }
    }

    /** Returns the status of each of the decks.  */
    fun fetchSmall(context: Context): SmallWidgetStatus = MetaDB.getWidgetSmallStatus(context)

    fun fetchDue(context: Context): Int = MetaDB.getNotificationStatus(context)

    private suspend fun querySmallWidgetStatus(): SmallWidgetStatus =
        withCol {
            val total = sched.allDecksCounts()
            val eta = sched.eta(total, false)
            SmallWidgetStatus(total.count(), eta)
        }
}
