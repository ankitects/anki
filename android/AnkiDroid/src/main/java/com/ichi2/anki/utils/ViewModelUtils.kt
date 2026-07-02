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

package com.ichi2.anki.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

sealed class InitStatus {
    data object Pending : InitStatus()

    data object InProgress : InitStatus()

    data object Completed : InitStatus()

    data class Failed(
        val exception: Exception,
    ) : InitStatus()
}

/**
 * Exposes the progress of the `init { }` method of a [ViewModel][androidx.lifecycle.ViewModel] via
 * [flowOfInitStatus]
 *
 * usage:
 * ```kotlin
 * class VM: ViewModel(), ViewModelDelayedInitializer {
 *     override val flowOfInitStatus: MutableStateFlow<InitStatus> = MutableStateFlow(InitStatus.PENDING)
 *     override val scope: CoroutineScope
 *         get() = viewModelScope
 *
 *     init {
 *         delayedInit {
 *             // code here
 *         }
 *     }
 * }
 * ```
 */
interface ViewModelDelayedInitializer {
    /** A flow to track how the `init { }` executed */
    val flowOfInitStatus: MutableStateFlow<InitStatus>

    val scope: CoroutineScope

    /** Called inside the `init { }` block of a ViewModel to track init progress */
    fun delayedInit(block: suspend () -> Unit) {
        scope.launch {
            flowOfInitStatus.value = InitStatus.InProgress
            try {
                Timber.d("init started")
                block()
                Timber.d("init completed")
                flowOfInitStatus.value = InitStatus.Completed
            } catch (e: Exception) {
                Timber.w(e, "Failed to initialize ViewModel")
                flowOfInitStatus.value = InitStatus.Failed(e)
            }
        }
    }
}
