/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.widget.RemoteViews
import androidx.annotation.CheckResult
import com.ichi2.widget.AppWidgetId.Companion.INVALID_APPWIDGET_ID

/**
 * Each widget has a unique identifier. This id allows to associate to each widget the preferences associated to this widget.
 * This class encapsulate such an id.
 *
 * This ID may not be valid, check for [INVALID_APPWIDGET_ID] when using this value.
 */
@JvmInline
value class AppWidgetId(
    val id: Int,
) {
    companion object {
        // TODO: consider a different design, Intent classes/nullables
        /**
         * Returns the [com.ichi2.widget.AppWidgetId] extra, or [INVALID_APPWIDGET_ID]
         *
         * @see AppWidgetManager.EXTRA_APPWIDGET_ID
         */
        @CheckResult
        fun Intent.getAppWidgetId() = AppWidgetId(getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))

        /**
         * Sets the [`EXTRA_APPWIDGET_ID`][AppWidgetManager.EXTRA_APPWIDGET_ID] for the intent
         */
        fun Intent.updateWidget(appWidgetId: AppWidgetId) = putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId.id)

        /**
         * A sentinel value that the AppWidget manager will never return as a appWidgetId.
         */
        val INVALID_APPWIDGET_ID = AppWidgetId(AppWidgetManager.INVALID_APPWIDGET_ID)
    }
}

/**
 * Represents a sequence of [AppWidgetId].
 */
@JvmInline
value class AppWidgetIds(
    val ids: IntArray,
) : Iterable<AppWidgetId> {
    override fun iterator() = ids.asSequence().map { AppWidgetId(it) }.iterator()

    companion object {
        fun of(ids: IntArray?) = ids?.let { AppWidgetIds(it) }
    }
}

/**
 * Set the RemoteViews to use for the specified appWidgetId.
 *
 * Note that the RemoteViews parameter will be cached by the AppWidgetService, and hence should
 * contain a complete representation of the widget. For performing partial widget updates, see
 * [AppWidgetManager.partiallyUpdateAppWidget].
 *
 * It is okay to call this method both inside an [AppWidgetManager.ACTION_APPWIDGET_UPDATE]
 *  * broadcast, and outside of the handler.
 * This method will only work when called from the uid that owns the AppWidget provider.
 *
 * The total Bitmap memory used by the RemoteViews object cannot exceed that required to
 * fill the screen 1.5 times, ie. (screen width x screen height x 4 x 1.5) bytes.
 *
 * @param appWidgetId      The AppWidget instance for which to set the RemoteViews.
 * @param views         The RemoteViews object to show.
 */
fun AppWidgetManager.updateAppWidget(
    appWidgetId: AppWidgetId,
    views: RemoteViews,
) {
    this.updateAppWidget(appWidgetId.id, views)
}

/**
 * Set the RemoteViews to use for the specified appWidgetIds.
 *
 * Note that the RemoteViews parameter will be cached by the AppWidgetService, and hence should
 * contain a complete representation of the widget. For performing partial widget updates, see
 * [AppWidgetManager.partiallyUpdateAppWidget]
 *
 * It is okay to call this method both inside an [AppWidgetManager.ACTION_APPWIDGET_UPDATE]
 * broadcast, and outside of the handler.
 * This method will only work when called from the uid that owns the AppWidget provider.
 *
 * The total Bitmap memory used by the RemoteViews object cannot exceed that required to
 * fill the screen 1.5 times, ie. (screen width x screen height x 4 x 1.5) bytes.
 *
 * @param appWidgetIds The AppWidget instances for which to set the RemoteViews.
 * @param views The RemoteViews object to show.
 *
 */
fun AppWidgetManager.updateAppWidget(
    appWidgetIds: AppWidgetIds,
    views: RemoteViews,
) {
    this.updateAppWidget(appWidgetIds.ids, views)
}

/**
 * Get the list of appWidgetIds that have been bound to the given AppWidget provider.
 *
 * @param provider The [android.content.BroadcastReceiver] that is the
 *  AppWidget provider to find appWidgetIds for.
 */
// Ex => Strongly typed extension
fun AppWidgetManager.getAppWidgetIdsEx(provider: ComponentName): AppWidgetIds = AppWidgetIds(getAppWidgetIds(provider))

/**
 * Get the extras associated with a given widget instance.
 *
 * The extras can be used to embed additional information about this widget to be accessed
 * by the associated widget's AppWidgetProvider.
 *
 * @see [AppWidgetManager.updateAppWidgetOptions]
 *
 * @param id The AppWidget instance for which to set the RemoteViews.
 * @return The options associated with the given widget instance.
 */
fun AppWidgetManager.getAppWidgetOptions(id: AppWidgetId) = getAppWidgetOptions(id.id)
