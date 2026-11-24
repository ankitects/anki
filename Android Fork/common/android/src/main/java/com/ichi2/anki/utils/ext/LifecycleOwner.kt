// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.withStarted

/** Suspends until this owner's lifecycle is at least [Lifecycle.State.STARTED], cancelling if destroyed. */
// TODO: Add a lint rule recommending awaitStarted() instead of empty withStarted lambdas.
suspend fun LifecycleOwner.awaitStarted() {
    withStarted {}
}
