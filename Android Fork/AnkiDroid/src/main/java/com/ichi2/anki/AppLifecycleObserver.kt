// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ichi2.widget.WidgetStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

class AppLifecycleObserver(
    private val context: Context,
) : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)

        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return

        // A sync may hold the collection queue. Keep the main thread free to handle
        // WorkManager's foreground service timeout while waiting for the collection.
        owner.lifecycleScope.launch {
            try {
                if (CollectionManager.withOpenColOrNull { true } == true) {
                    WidgetStatus.updateInBackground(context)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e)
            }
        }
    }
}
