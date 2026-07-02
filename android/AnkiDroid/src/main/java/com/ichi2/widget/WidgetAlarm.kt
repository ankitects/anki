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

package com.ichi2.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import timber.log.Timber
import kotlin.time.Duration.Companion.minutes

/**
 * Custom action to update the widget.
 * This constant is used to trigger the widget update via a custom broadcast intent.
 */
const val ACTION_UPDATE_WIDGET = "com.ichi2.widget.ACTION_UPDATE_WIDGET"

/**
 * Provides the AlarmManager instance.
 *
 * @param context the context of the application
 * @return the AlarmManager instance
 */
private fun alarmManager(context: Context): AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

/**
 * Retrieves or creates a PendingIntent for the widget.
 *
 * @param context the context of the application
 * @param appWidgetId the ID of the widget
 * @param widgetClass the class of the widget provider
 * @param create whether to create a new PendingIntent or just retrieve it
 * @return the PendingIntent for the widget
 */
private fun getPendingIntent(
    context: Context,
    appWidgetId: AppWidgetId,
    widgetClass: Class<out AnalyticsWidgetProvider>,
    create: Boolean,
): PendingIntent? {
    val intent =
        Intent(context, widgetClass).apply {
            action = ACTION_UPDATE_WIDGET
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId.id)
        }
    return PendingIntent.getBroadcast(
        context,
        appWidgetId.id,
        intent,
        if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        },
    )
}

/**
 * Ensure a recurring alarm is set to update the widget every 1 minute.
 *
 * If the alarm is already set for the widget, this method does nothing.
 * This ensures that multiple alarms are not created for the same widget,
 * preventing potential performance issues or unexpected behavior.
 *
 * @param context the context of the application
 * @param appWidgetId the ID of the widget
 * @param widgetClass the class of the widget provider
 */
fun setRecurringAlarm(
    context: Context,
    appWidgetId: AppWidgetId,
    widgetClass: Class<out AnalyticsWidgetProvider>,
) {
    val pendingIntent = getPendingIntent(context, appWidgetId, widgetClass, create = false)

    if (pendingIntent != null) {
        Timber.v("Recurring alarm PendingIntent already exists for widget ID: $appWidgetId")
        return
    }

    Timber.v("Creating a new recurring alarm PendingIntent for widget ID: $appWidgetId")

    val alarmManager = alarmManager(context)
    val newPendingIntent = getPendingIntent(context, appWidgetId, widgetClass, create = true) ?: return

    alarmManager.setRepeating(
        AlarmManager.ELAPSED_REALTIME,
        SystemClock.elapsedRealtime() + 1.minutes.inWholeMilliseconds,
        1.minutes.inWholeMilliseconds,
        newPendingIntent,
    )
}

/**
 * Cancels the recurring alarm for the widget.
 *
 * @param context the context of the application
 * @param appWidgetId the ID of the widget
 * @param widgetClass the class of the widget provider
 */
fun cancelRecurringAlarm(
    context: Context,
    appWidgetId: AppWidgetId,
    widgetClass: Class<out AnalyticsWidgetProvider>,
) {
    val pendingIntent = getPendingIntent(context, appWidgetId, widgetClass, create = true)
    val alarmManager = alarmManager(context)
    Timber.d("Canceling recurring alarm for widget ID: $appWidgetId")
    if (pendingIntent != null) {
        alarmManager.cancel(pendingIntent)
    }
}

private fun AppWidgetManager.getAppWidgetIds(
    context: Context,
    widgetClass: Class<out AnalyticsWidgetProvider>,
): List<AppWidgetId>? {
    val componentName = ComponentName(context, widgetClass)
    val ids = this.getAppWidgetIds(componentName)

    if (ids.isEmpty()) return null

    return ids.map { AppWidgetId(it) }
}

fun restoreRecurringAlarms(context: Context) {
    val appWidgetManager = getAppWidgetManager(context) ?: return

    for (widgetClass in RECURRING_WIDGETS) {
        val activeIds =
            try {
                appWidgetManager.getAppWidgetIds(context, widgetClass)
            } catch (e: SecurityException) {
                Timber.w(e, "Failed to get widget IDs for %s because security exception", widgetClass.simpleName)
                null
            } catch (e: Exception) {
                Timber.w(e, "Failed to get widget IDs for %s", widgetClass.simpleName)
                null
            } ?: continue

        Timber.d("Restoring %d alarms for %s", activeIds.size, widgetClass.simpleName)

        for (id in activeIds) {
            try {
                setRecurringAlarm(
                    context,
                    id,
                    widgetClass,
                )
            } catch (e: SecurityException) {
                Timber.w(
                    e,
                    "Failed to restore alarms for %s because system alarm limit reached",
                    widgetClass.simpleName,
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to restore alarms for %s", widgetClass.simpleName)
            }
        }
    }
}
