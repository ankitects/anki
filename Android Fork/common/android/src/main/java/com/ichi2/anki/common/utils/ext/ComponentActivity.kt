// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Run [action] when this activity is destroyed because it is finishing.
 *
 * Register once per activity instance, during `onCreate`, after initializing the resources to release.
 * This is a cleanup opportunity for resources owned exclusively by the activity's saved state, such
 * as files referenced by its saved bundle.
 *
 * Why destruction or `ViewModel.onCleared()` alone is insufficient:
 * - Configuration changes destroy the activity, but retain its ViewModels and saved state.
 * - Android can also destroy an activity without finishing it (for example, "Don't keep activities").
 *   `ViewModel.onCleared()` is called, but Android retains the saved bundle for later restoration.
 *
 * This is best-effort cleanup and should not be relied upon, as process termination can skip callbacks
 *
 * The action runs synchronously on the main thread during destruction.
 *
 * Do not use the activity or coroutine scope, as they will likely be unusable.
 */
@MainThread
fun ComponentActivity.onPermanentDismissal(action: () -> Unit) {
    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (isFinishing) action()
            }
        },
    )
}
