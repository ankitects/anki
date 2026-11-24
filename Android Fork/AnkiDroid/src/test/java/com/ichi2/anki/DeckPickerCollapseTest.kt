// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.collection.OpChanges
import anki.decks.DeckTreeNode
import anki.decks.SetDeckCollapsedRequest
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerCollapseTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `expanding a deck again while it is collapsing restores it`() {
        val saving = CountDownLatch(1)
        val releaseSave = CountDownLatch(1)
        val pauseSave = AtomicBoolean(true)
        useBackend {
            object : Backend() {
                override fun setDeckCollapsed(
                    deckId: Long,
                    collapsed: Boolean,
                    scope: SetDeckCollapsedRequest.Scope,
                ): OpChanges {
                    if (pauseSave.compareAndSet(true, false)) {
                        saving.countDown()
                        check(releaseSave.await(10, TimeUnit.SECONDS)) { "Timed out waiting to save the deck" }
                    }
                    return super.setDeckCollapsed(deckId, collapsed, scope)
                }
            }
        }
        val parent = addDeck("Parent")
        addDeck("Parent::Child")
        val viewModel = DeckPickerViewModel()

        val initiallyCollapsed = col.decks.get(parent)!!.collapsed

        withCollectionThread {
            val initialRefresh = viewModel.reloadDeckCounts()
            advanceRobolectricLooperUntil { initialRefresh.isCompleted }
            val first = viewModel.toggleDeckExpand(parent)
            assertTrue(saving.await(5, TimeUnit.SECONDS))
            val second = viewModel.toggleDeckExpand(parent)
            releaseSave.countDown()
            advanceRobolectricLooperUntil { first.isCompleted && second.isCompleted }
        }

        assertEquals(initiallyCollapsed, col.decks.get(parent)!!.collapsed)
        assertEquals(initiallyCollapsed, viewModel.dueTree?.find(parent)?.collapsed)
    }

    @Test
    fun `a refresh does not undo a collapse saved while it reads the decks`() {
        val readingDecks = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val pauseRead = AtomicBoolean(false)
        useBackend {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode {
                    if (pauseRead.compareAndSet(true, false)) {
                        readingDecks.countDown()
                        check(releaseRead.await(10, TimeUnit.SECONDS)) { "Timed out waiting to read the decks" }
                    }
                    return super.deckTree(now)
                }
            }
        }
        val parent = addDeck("Parent")
        addDeck("Parent::Child")
        val viewModel = DeckPickerViewModel()

        val initiallyCollapsed = col.decks.get(parent)!!.collapsed

        withCollectionThread {
            val initialRefresh = viewModel.reloadDeckCounts()
            advanceRobolectricLooperUntil { initialRefresh.isCompleted }
            addDeck("Other")
            pauseRead.set(true)
            val refresh = viewModel.reloadDeckCounts()
            assertTrue(readingDecks.await(5, TimeUnit.SECONDS))
            val toggle = viewModel.toggleDeckExpand(parent)
            releaseRead.countDown()
            advanceRobolectricLooperUntil { refresh.isCompleted && toggle.isCompleted }
        }

        assertEquals(!initiallyCollapsed, col.decks.get(parent)!!.collapsed)
        assertEquals(!initiallyCollapsed, viewModel.dueTree?.find(parent)?.collapsed)
    }

    private fun useBackend(backend: () -> Backend) = runBlocking { backendOverride.replaceWith(backend) }

    private fun withCollectionThread(block: () -> Unit) {
        val previousQueue = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
        try {
            block()
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }
}
