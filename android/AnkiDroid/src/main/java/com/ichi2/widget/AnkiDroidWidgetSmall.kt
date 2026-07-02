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

import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.R
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.android.SdCard
import com.ichi2.anki.compat.CompatHelper.Companion.registerReceiverCompat
import timber.log.Timber
import kotlin.math.sqrt

/**
 * AnkiDroidWidgetSmall is a small-sized home screen widget for the AnkiDroid application.
 * This widget displays the number of due cards and an estimated review time.
 * It updates periodically and can respond to certain actions like resizing.
 */

class AnkiDroidWidgetSmall : AnalyticsWidgetProvider() {
    override fun performUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: AppWidgetIds,
        usageAnalytics: UsageAnalytics,
    ) {
        WidgetStatus.updateInBackground(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val preferences = context.sharedPrefs()
        preferences.edit(commit = true) { putBoolean("widgetSmallEnabled", true) }
        DayRolloverAlarm.scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val preferences = context.sharedPrefs()
        preferences.edit(commit = true) { putBoolean("widgetSmallEnabled", false) }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action.contentEquals("com.sec.android.widgetapp.APPWIDGET_RESIZE")) {
            updateWidgetDimensions(context, RemoteViews(context.packageName, R.layout.widget_small), AnkiDroidWidgetSmall::class.java)
        }
        super.onReceive(context, intent)
    }

    class UpdateService : Service() {
        /**
         * Updates the UI of [AnkiDroidWidgetSmall]
         *
         * Externals callers should use [WidgetStatus.updateInBackground]
         */
        fun doUpdate(context: Context) {
            val appWidgetManager = getAppWidgetManager(context) ?: return
            val remoteViews = buildUpdate(context)
            appWidgetManager.updateAppWidget(ComponentName(context, AnkiDroidWidgetSmall::class.java), remoteViews)
        }

        @Deprecated("Implement onStartCommand(Intent, int, int) instead.") // TODO
        override fun onStart(intent: Intent, startId: Int) {
            Timber.i("SmallWidget: OnStart")
            val manager = getAppWidgetManager(this) ?: return
            val updateViews = buildUpdate(this)
            val thisWidget = ComponentName(this, AnkiDroidWidgetSmall::class.java)
            manager.updateAppWidget(thisWidget, updateViews)
        }

        @get:LayoutRes
        private val widgetSmallLayout: Int
            get() {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return R.layout.widget_small
                return if (disableMaterialYouDynamicColor) R.layout.widget_small_unthemed else R.layout.widget_small
            }

        private fun buildUpdate(context: Context): RemoteViews {
            Timber.d("updating small widget UI")
            val updateViews = RemoteViews(context.packageName, widgetSmallLayout)
            val mounted = SdCard.isMounted
            if (!mounted) {
                updateViews.setViewVisibility(R.id.widget_due, View.INVISIBLE)
                updateViews.setViewVisibility(R.id.widget_eta, View.INVISIBLE)
                updateViews.setViewVisibility(R.id.ankidroid_widget_small_finish_layout, View.GONE)
                if (mountReceiver == null) {
                    mountReceiver =
                        object : BroadcastReceiver() {
                            override fun onReceive(
                                @Suppress("LocalVariableName") context_doNotUse: Context,
                                intent: Intent,
                            ) {
                                // baseContext() is null, applicationContext() throws a NPE,
                                // context may not have the locale override from AnkiDroidApp
                                val action = intent.action
                                if (action != null && action == Intent.ACTION_MEDIA_MOUNTED) {
                                    Timber.d("mountReceiver - Action = Media Mounted")
                                    if (remounted) {
                                        WidgetStatus.updateInBackground(appContext)
                                        remounted = false
                                        if (mountReceiver != null) {
                                            appContext.unregisterReceiver(mountReceiver)
                                        }
                                    } else {
                                        remounted = true
                                    }
                                }
                            }
                        }
                    val iFilter = IntentFilter()
                    iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED)
                    iFilter.addDataScheme("file")
                    appContext.registerReceiverCompat(mountReceiver, iFilter, ContextCompat.RECEIVER_EXPORTED)
                }
            } else {
                // Compute the total number of cards due.
                val (dueCardsCount, eta) = WidgetStatus.fetchSmall(context)
                if (dueCardsCount == 0) {
                    updateViews.setViewVisibility(R.id.ankidroid_widget_small_finish_layout, View.VISIBLE)
                    updateViews.setViewVisibility(R.id.widget_eta, View.INVISIBLE)
                    updateViews.setViewVisibility(R.id.widget_due, View.INVISIBLE)
                } else {
                    updateViews.setViewVisibility(R.id.ankidroid_widget_small_finish_layout, View.INVISIBLE)
                    updateViews.setViewVisibility(R.id.widget_due, View.VISIBLE)
                    updateViews.setTextViewText(R.id.widget_due, dueCardsCount.toString())
                    updateViews.setContentDescription(
                        R.id.widget_due,
                        context.resources.getQuantityString(R.plurals.widget_cards_due, dueCardsCount, dueCardsCount),
                    )

                    if (eta <= 0) {
                        updateViews.setViewVisibility(R.id.widget_eta, View.INVISIBLE)
                    } else {
                        updateViews.setViewVisibility(R.id.widget_eta, View.VISIBLE)
                        val etaText = if (Build.VERSION.SDK_INT >= 31) "⏱$eta" else "$eta"
                        updateViews.setTextViewText(R.id.widget_eta, etaText)
                        updateViews.setContentDescription(
                            R.id.widget_eta,
                            context.resources.getQuantityString(R.plurals.widget_eta, eta, eta),
                        )
                    }
                }
            }

            // Add a click listener to open Anki from the icon.
            // This should be always there, whether there are due cards or not.
            val ankiDroidIntent = Intent(context, IntentHandler::class.java)
            ankiDroidIntent.action = Intent.ACTION_MAIN
            ankiDroidIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            val pendingAnkiDroidIntent =
                PendingIntentCompat.getActivity(
                    context,
                    0,
                    ankiDroidIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT,
                    false,
                )
            updateViews.setOnClickPendingIntent(R.id.ankidroid_widget_small_button, pendingAnkiDroidIntent)
            updateWidgetDimensions(context, updateViews, AnkiDroidWidgetSmall::class.java)
            return updateViews
        }

        override fun onBind(arg0: Intent): IBinder? {
            Timber.d("onBind")
            return null
        }
    }

    companion object {
        private var mountReceiver: BroadcastReceiver? = null
        private var remounted = false

        private fun updateWidgetDimensions(
            context: Context,
            updateViews: RemoteViews,
            cls: Class<*>,
        ) {
            val manager = getAppWidgetManager(context) ?: return
            val ids = manager.getAppWidgetIdsEx(ComponentName(context, cls))
            for (id in ids) {
                val scale = context.resources.displayMetrics.density
                val options = manager.getAppWidgetOptions(id)
                var width: Float
                var height: Float
                if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
                    height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()
                } else {
                    width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat()
                    height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).toFloat()
                }
                var horizontal: Int
                var vertical: Int
                var text: Float
                if (width / height > 0.8) {
                    horizontal = (((width - height * 0.8) / 2 + 4) * scale + 0.5f).toInt()
                    vertical = (4 * scale + 0.5f).toInt()
                    text = (sqrt(height * 0.8 / width) * 18).toFloat()
                } else {
                    vertical = (((height - width * 1.25) / 2 + 4) * scale + 0.5f).toInt()
                    horizontal = (4 * scale + 0.5f).toInt()
                    text = (sqrt(width * 1.25 / height) * 18).toFloat()
                }
                updateViews.setTextViewTextSize(R.id.widget_due, TypedValue.COMPLEX_UNIT_SP, text)
                updateViews.setTextViewTextSize(R.id.widget_eta, TypedValue.COMPLEX_UNIT_SP, text)
                updateViews.setViewPadding(R.id.ankidroid_widget_text_layout, horizontal, vertical, horizontal, vertical)
            }
        }
    }
}
