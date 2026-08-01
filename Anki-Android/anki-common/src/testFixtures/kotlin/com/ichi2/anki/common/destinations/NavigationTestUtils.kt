// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.destinations

import android.app.Activity
import androidx.test.core.app.ActivityScenario

/**
 * Launches an [ActivityScenario] for the activity targeted by [destination].
 *
 * `RobolectricTest` implements [DeferredNavigation], so this helper simplifies callers.
 *
 * ```kt
 * launchActivity<SingleFragmentActivity>(StatisticsDestination).use { ... }
 * ```
 *
 * @see ActivityScenario.launch
 */
context(_: DeferredNavigation)
fun <T : Activity> launchActivity(destination: Destination): ActivityScenario<T> = ActivityScenario.launch(destination.toIntent())
