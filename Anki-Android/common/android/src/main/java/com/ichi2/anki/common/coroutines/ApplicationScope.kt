// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.common.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * [CoroutineScope] tied to the [android.app.Application], allowing executing of tasks
 * which should execute as long as the app is running.
 *
 * This scope is bound by default to [Dispatchers.Main.immediate][kotlinx.coroutines.MainCoroutineDispatcher.immediate].
 * Use an alternate dispatcher if the main thread is not required: [Dispatchers.Default] or [Dispatchers.IO]
 *
 * This scope will not be cancelled; exceptions are handled by [SupervisorJob]
 *
 * See: [Operations that shouldn't be cancelled in Coroutines](https://medium.com/androiddevelopers/coroutines-patterns-for-work-that-shouldnt-be-cancelled-e26c40f142ad#d425)
 *
 * This replicates the manner which `lifecycleScope`/`viewModelScope` is exposed in Android.
 */
// lazy init required due to kotlinx-coroutines-test 1.10.0:
// Main was accessed when the platform dispatcher was absent and the test dispatcher
// was unset
val applicationScope: CoroutineScope by lazy {
    CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
