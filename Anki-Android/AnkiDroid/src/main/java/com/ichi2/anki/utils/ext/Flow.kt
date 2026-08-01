/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.utils.ext

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.common.utils.android.HandlerUtils
import com.ichi2.anki.common.utils.android.isRobolectric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun <T> Flow<T>.collectLatestIn(
    scope: CoroutineScope,
    action: suspend (value: T) -> Unit,
): Job =
    scope.launch {
        collectLatest(action)
    }

fun <T> Flow<T>.collectIn(
    scope: CoroutineScope,
    collector: FlowCollector<T>,
): Job =
    scope.launch {
        collect(collector)
    }

context(fragment: Fragment)
fun <T> Flow<T>.launchCollectionInLifecycleScope(block: suspend (T) -> Unit) {
    fragment.lifecycleScope.launch {
        fragment.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            this@launchCollectionInLifecycleScope.collect {
                if (isRobolectric) {
                    fragment.lifecycle.postOnNewHandlerIfAlive { block(it) }
                } else {
                    block(it)
                }
            }
        }
    }
}

// Workaround a bug in overload resolution. Replace with the following when the compiler is fixed:
//  state: Lifecycle.State = Lifecycle.State.STARTED
context(activity: AnkiActivity)
fun <T> Flow<T>.launchCollectionInLifecycleScope(block: suspend (T) -> Unit) {
    launchCollectionInLifecycleScope(Lifecycle.State.STARTED, block)
}

context(activity: AnkiActivity)
fun <T> Flow<T>.launchCollectionInLifecycleScope(
    state: Lifecycle.State,
    block: suspend (T) -> Unit,
) {
    activity.lifecycleScope.launch {
        activity.lifecycle.repeatOnLifecycle(state) {
            this@launchCollectionInLifecycleScope.collect {
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
fun <T> StateFlow<T>.launchCollectionInLifecycleScope(block: suspend (T) -> Unit) {
    activity.lifecycleScope.launch {
        var lastValue: T? = null
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            this@launchCollectionInLifecycleScope.collect {
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
