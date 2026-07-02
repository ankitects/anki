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

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import androidx.annotation.CallSuper
import com.ichi2.anki.IntentHandler.Companion.grantedStoragePermissions
import com.ichi2.anki.analytics.UsageAnalytics
import timber.log.Timber

/**
 * AnalyticsWidgetProvider is an abstract base class for App Widgets that integrates
 * with UsageAnalytics to send analytics events when the widget is enabled, disabled,
 * or updated.
 *
 * This class should always be used as the base class for App Widgets in this application.
 * Direct usage of AppWidgetProvider should be avoided.
 * TODO: Add a lint rule to forbid the direct use of AppWidgetProvider.
 *
 * Subclasses must override [performUpdate] to define the widget update logic.
 *
 * - To use this class, extend it and implement the [performUpdate] method.
 * - Override [onUpdate] if additional logic is required beyond [performUpdate].
 */
abstract class AnalyticsWidgetProvider : AppWidgetProvider() {
    /**
     * Called when the widget is enabled. Sends an analytics event.
     *
     * @param context The context in which the receiver is running.
     */
    @CallSuper
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Timber.d("${this.javaClass.name}: Widget enabled")
        UsageAnalytics.sendAnalyticsEvent(this.javaClass.simpleName, "enabled")
    }

    /**
     * Called when the widget is disabled. Sends an analytics event.
     *
     * @param context The context in which the receiver is running.
     */
    @CallSuper
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Timber.d("${this.javaClass.name}: Widget disabled")
        UsageAnalytics.sendAnalyticsEvent(this.javaClass.simpleName, "disabled")
    }

    @CallSuper
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        Timber.v("${this.javaClass.name}: onReceive: %s", intent.action)
    }

    /**
     * Called to update the widget. Checks storage permissions and delegates to [performUpdate].
     *
     * @param context The context in which the receiver is running.
     * @param appWidgetManager The AppWidgetManager instance to use for updating widgets.
     * @param appWidgetIds The app widget IDs to update.
     */
    final override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        if (runCatching { grantedStoragePermissions(context, showToast = false) }.getOrNull() != true) {
            Timber.w("Opening widget ${this.javaClass.name} without storage access")
            return
        }
        // Pass usageAnalytics to performUpdate
        Timber.d("${this.javaClass.name}: performUpdate")
        performUpdate(context, appWidgetManager, AppWidgetIds(appWidgetIds), UsageAnalytics)
    }

    /**
     * Override this method to implement Widget functionality
     *
     * Called when the [AnalyticsWidgetProvider] is asked to provide [RemoteViews] for a set of Widgets AND the Anki collection is accessible.
     *
     * @see AppWidgetProvider.onUpdate
     *
     * @param context The context in which the receiver is running.
     * @param appWidgetManager The AppWidgetManager instance to use for updating widgets.
     * @param appWidgetIds The app widget IDs to update.
     * @param usageAnalytics The UsageAnalytics instance for logging analytics events.
     */

    abstract fun performUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: AppWidgetIds,
        usageAnalytics: UsageAnalytics,
    )
}
