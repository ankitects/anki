// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import java.io.Closeable
import kotlin.test.assertFalse

/** Checks that coroutines finish when their fragment or view lifecycle is destroyed. */
class FragmentCoroutineTracker(
    private val fragmentManager: FragmentManager,
) : FragmentManager.FragmentLifecycleCallbacks(),
    Closeable {
    // Observe lifecycles without accessing their scopes: doing so would register cancellation
    // early and mask the startup race this tracker is meant to detect.
    private val lifecycles = mutableListOf<Pair<String, Lifecycle>>()

    init {
        fragmentManager.registerFragmentLifecycleCallbacks(this, true)
    }

    override fun onFragmentCreated(
        fm: FragmentManager,
        f: Fragment,
        savedInstanceState: Bundle?,
    ) {
        lifecycles.add("${f.javaClass.simpleName} fragment" to f.lifecycle)
    }

    override fun onFragmentViewCreated(
        fm: FragmentManager,
        f: Fragment,
        v: View,
        savedInstanceState: Bundle?,
    ) {
        lifecycles.add("${f.javaClass.simpleName} view" to f.viewLifecycleOwner.lifecycle)
    }

    fun assertDestroyedLifecyclesAreIdle() {
        for ((name, lifecycle) in lifecycles) {
            if (lifecycle.currentState != Lifecycle.State.DESTROYED) continue
            val job = lifecycle.coroutineScope.coroutineContext[Job]!!
            assertFalse(job.children.any { it.isActive }, "$name still has active coroutines after destruction")
        }
    }

    override fun close() {
        fragmentManager.unregisterFragmentLifecycleCallbacks(this)
        // Cancel leaked collectors even when an assertion failed, to isolate subsequent tests.
        lifecycles.forEach { (_, lifecycle) -> lifecycle.coroutineScope.cancel() }
    }
}
