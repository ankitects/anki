// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.utils

import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.EmptyAnkiActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class MessageQueueObserverTest : RobolectricTest() {
    private val queue = MessageQueue<String>()

    @Test
    fun `message shown before a stop is acknowledged once dismissed`() {
        val dismissed = CompletableDeferred<Unit>()
        val shown = mutableListOf<String>()
        observeInFragment {
            observeMessages(queue.messages, queue::acknowledge) {
                shown += it
                dismissed.await()
            }
        }.use { host ->
            queue.enqueue("result")
            advanceRobolectricLooper()

            host.pause().stop()
            host.start().resume()
            advanceRobolectricLooper()
            assertEquals(listOf("result"), queue.messages.value.map { it.message })

            dismissed.complete(Unit)
            advanceRobolectricLooper()

            assertEquals(listOf("result"), shown)
            assertEquals(emptyList(), queue.messages.value)
        }
    }

    @Test
    fun `message removed while shown is dismissed`() {
        val shown = mutableListOf<String>()
        var dismissed = false
        observeInFragment {
            observeMessages(queue.messages, queue::acknowledge) {
                shown += it
                try {
                    awaitCancellation()
                } finally {
                    if (it == "stale") dismissed = true
                }
            }
        }.use {
            queue.enqueue("stale")
            queue.enqueue("next")
            advanceRobolectricLooper()
            assertFalse(dismissed, "a message is shown until it is dismissed or removed")

            queue.acknowledge(
                queue.messages.value
                    .first()
                    .id,
            )
            advanceRobolectricLooper()

            assertTrue(dismissed)
            assertEquals(listOf("stale", "next"), shown)
        }
    }

    @Test
    fun `cancelling the wait dismisses the dialog`() =
        runTest {
            val waiting =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    AlertDialog.Builder(ContextThemeWrapper(targetContext, CommonR.style.Theme_AnkiDroid)).showAndAwaitDismissal {}
                }
            val dialog = ShadowDialog.getLatestDialog()

            waiting.cancel()

            assertTrue(shadowOf(dialog).hasBeenDismissed())
        }

    private fun observeInFragment(observe: Fragment.() -> Unit): ActivityController<EmptyAnkiActivity> =
        Robolectric.buildActivity(EmptyAnkiActivity::class.java).setup().also { host ->
            val fragment = ObservingFragment().apply { this.observe = observe }
            host.get().supportFragmentManager.commitNow { add(android.R.id.content, fragment) }
        }

    class ObservingFragment : Fragment() {
        var observe: Fragment.() -> Unit = {}

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ) = View(inflater.context)

        override fun onViewCreated(
            view: View,
            savedInstanceState: Bundle?,
        ) = observe()
    }
}
