// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ComponentInfo
import androidx.annotation.CheckResult
import com.ichi2.anki.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.anki.compat.CompatHelper.Companion.queryIntentActivitiesCompat
import com.ichi2.anki.compat.GET_ACTIVITIES_L
import com.ichi2.anki.compat.GET_PROVIDERS_L
import com.ichi2.anki.compat.GET_RECEIVERS_L
import com.ichi2.anki.compat.GET_SERVICES_L
import com.ichi2.anki.compat.MATCH_ALL_L
import com.ichi2.anki.compat.PackageInfoFlagsCompat
import com.ichi2.anki.compat.ResolveInfoFlagsCompat

/**
 * The set of AnkiDroid components which can be triggered from **outside** a running AnkiDroid
 * process: by the launcher, another app, the widget host, or the system.
 *
 * This exists so we can ensure a consistent & crash-free startup experience.
 *
 * @see com.ichi2.anki.ActivityStartupMetaTest for the equivalent activity-only coverage guard
 */
object ExternalEntryPoints {
    /**
     * A single externally-reachable manifest component, by declared kind.
     */
    sealed class EntryPoint {
        abstract val className: String

        data class Activity(
            override val className: String,
        ) : EntryPoint()

        /**
         * An `<activity-alias>`: reachable like an [Activity] but backed by no class of its own.
         *
         * @param className the name of the component
         * @param targetActivity the name of the class of the activity to launch
         */
        data class ActivityAlias(
            override val className: String,
            val targetActivity: String,
        ) : EntryPoint()

        data class Service(
            override val className: String,
        ) : EntryPoint()

        data class Receiver(
            override val className: String,
        ) : EntryPoint()

        data class Provider(
            override val className: String,
        ) : EntryPoint()

        /**
         * A widget-configuration activity, launched by the widget host via
         * [AppWidgetManager.ACTION_APPWIDGET_CONFIGURE] even though it is not `exported`.
         */
        data class WidgetConfig(
            override val className: String,
        ) : EntryPoint()

        /**
         * A [WidgetConfig] declared as an `<activity-alias>` forwarding to [targetActivity].
         *
         * @param className the name of the component
         * @param targetActivity the name of the class of the activity to launch
         */
        data class WidgetConfigAlias(
            override val className: String,
            val targetActivity: String,
        ) : EntryPoint()

        final override fun toString() = "${this::class.simpleName} $className"
    }

    /**
     * Every AnkiDroid-owned component (activity, service etc...) reachable from outside the app.
     *
     * See [EntryPoint] for component types.
     *
     * Excludes components included by libraries.
     */
    @CheckResult
    fun all(context: Context): List<EntryPoint> {
        val flags = PackageInfoFlagsCompat.of(GET_ACTIVITIES_L or GET_PROVIDERS_L or GET_RECEIVERS_L or GET_SERVICES_L)
        val packageInfo =
            context.getPackageInfoCompat(context.packageName, flags)
                ?: error("getPackageInfo failed")

        return buildList {
            fun addExported(
                components: Array<out ComponentInfo>?,
                buildEntryPoint: (String) -> EntryPoint,
            ) = components
                .orEmpty()
                .filter { it.exported && isAnkiDroid(it.name) }
                .forEach { add(buildEntryPoint(it.name)) }

            packageInfo.activities
                .orEmpty()
                .filter { it.exported && isAnkiDroid(it.name) }
                .forEach {
                    add(
                        it.toEntryPoint(
                            alias = EntryPoint::ActivityAlias,
                            activity = EntryPoint::Activity,
                        ),
                    )
                }

            addExported(packageInfo.services, EntryPoint::Service)
            addExported(packageInfo.receivers, EntryPoint::Receiver)
            addExported(packageInfo.providers, EntryPoint::Provider)

            // '<action android:name="...APPWIDGET_CONFIGURE'/> bypasses 'exported=true'.
            context.packageManager
                .queryIntentActivitiesCompat(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE),
                    ResolveInfoFlagsCompat.of(MATCH_ALL_L),
                ).map { it.activityInfo }
                .filter { isAnkiDroid(it.name) }
                .forEach {
                    add(
                        it.toEntryPoint(
                            alias = EntryPoint::WidgetConfigAlias,
                            activity = EntryPoint::WidgetConfig,
                        ),
                    )
                }
        }.distinct().sortedBy { it.className }
    }

    private fun isAnkiDroid(componentName: String) = componentName.startsWith("com.ichi2")

    /**
     * Maps an activity to an [EntryPoint]: [alias] when [ActivityInfo.targetActivity] is set (the
     * activity is an `<activity-alias>`), otherwise [activity].
     */
    private fun ActivityInfo.toEntryPoint(
        alias: (className: String, targetActivity: String) -> EntryPoint,
        activity: (className: String) -> EntryPoint,
    ): EntryPoint = targetActivity?.let { alias(name, it) } ?: activity(name)
}
