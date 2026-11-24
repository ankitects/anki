// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

package com.ichi2.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import androidx.annotation.CallSuper
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.analytics.AnalyticsEvent
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.StorageDecision
import timber.log.Timber

/**
 * AnalyticsWidgetProvider is an abstract base class for App Widgets that integrates
 * with [Analytics] to send analytics events when the widget is enabled, disabled,
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
    protected open val requiresCollection = true

    /**
     * Called when the widget is enabled. Sends an analytics event.
     *
     * @param context The context in which the receiver is running.
     */
    @CallSuper
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Timber.d("${this.javaClass.name}: Widget enabled")
        Analytics.send(AnalyticsEvent.WidgetEnabled(this.javaClass.simpleName))
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
        Analytics.send(AnalyticsEvent.WidgetDisabled(this.javaClass.simpleName))
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
     * Called to update the widget. Checks that storage is set up and delegates to [performUpdate].
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
        if (requiresCollection && CollectionHelper.storageDecision(context.sharedPrefs()) == StorageDecision.Undecided) {
            Timber.w("Opening widget ${this.javaClass.name} without storage access")
            return
        }
        Timber.d("${this.javaClass.name}: performUpdate")
        performUpdate(context, appWidgetManager, AppWidgetIds(appWidgetIds))
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
     */

    abstract fun performUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: AppWidgetIds,
    )
}
