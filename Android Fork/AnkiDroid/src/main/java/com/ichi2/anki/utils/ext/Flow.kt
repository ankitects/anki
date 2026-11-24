// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.common.utils.android.HandlerUtils
import com.ichi2.anki.common.utils.android.isRobolectric
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

context(fragment: Fragment)
fun <T> Flow<T>.collectInLifecycle(block: suspend (T) -> Unit) {
    fragment.lifecycleScope.launch {
        fragment.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            this@collectInLifecycle.collect {
                if (isRobolectric) {
                    fragment.lifecycle.postOnNewHandlerIfAlive { block(it) }
                } else {
                    block(it)
                }
            }
        }
    }
}

/**
 * Collects while the fragment's current view is started. Call from [Fragment.onViewCreated].
 */
context(fragment: Fragment)
fun <T> Flow<T>.collectInViewLifecycle(
    state: Lifecycle.State = Lifecycle.State.STARTED,
    block: suspend (T) -> Unit,
): Job {
    // Capture the owner before launching: the view may be destroyed or recreated before the job starts.
    val owner = fragment.viewLifecycleOwner
    return owner.lifecycleScope.launch {
        owner.repeatOnLifecycle(state) {
            this@collectInViewLifecycle.collect(block)
        }
    }
}

// Workaround a bug in overload resolution. Replace with the following when the compiler is fixed:
//  state: Lifecycle.State = Lifecycle.State.STARTED
context(activity: AnkiActivity)
fun <T> Flow<T>.collectInLifecycle(block: suspend (T) -> Unit) {
    collectInLifecycle(Lifecycle.State.STARTED, block)
}

context(activity: AnkiActivity)
fun <T> Flow<T>.collectInLifecycle(
    state: Lifecycle.State,
    block: suspend (T) -> Unit,
) {
    activity.lifecycleScope.launch {
        activity.lifecycle.repeatOnLifecycle(state) {
            this@collectInLifecycle.collect {
                if (isRobolectric) {
                    activity.lifecycle.postOnNewHandlerIfAlive { block(it) }
                } else {
                    block(it)
                }
            }
        }
    }
}

context(activity: AnkiActivity)
fun <T> StateFlow<T>.collectInLifecycle(block: suspend (T) -> Unit) {
    activity.lifecycleScope.launch {
        var lastValue: T? = null
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            this@collectInLifecycle.collect {
                // on re-resume, an unchanged value will be emitted for a StateFlow
                if (lastValue == it) return@collect
                lastValue = it
                if (isRobolectric) {
                    activity.lifecycle.postOnNewHandlerIfAlive { block(it) }
                } else {
                    block(it)
                }
            }
        }
    }
}

/**
 * Hack: lifecycleScope/runOnUiThread do not handle our test dispatcher overriding both IO and Main.
 * In tests, waitForAsyncTasksToComplete may be required.
 */
private fun Lifecycle.postOnNewHandlerIfAlive(block: suspend () -> Unit) {
    val lifecycle = this
    HandlerUtils.postOnNewHandler {
        if (lifecycle.currentState != Lifecycle.State.DESTROYED) {
            runBlocking { block() }
        }
    }
}
