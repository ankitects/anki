// SPDX-License-Identifier: GPL-3.0-or-later

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
