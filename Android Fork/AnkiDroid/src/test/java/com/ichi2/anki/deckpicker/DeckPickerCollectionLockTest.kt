// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.deckpicker.DeckPickerViewModel.FlattenedDeckList
import com.ichi2.anki.exception.CollectionLockedException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import net.ankiweb.rsdroid.Backend
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class DeckPickerCollectionLockTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    private val viewModels = ViewModelStore()
    private val viewModel by lazy {
        DeckPickerViewModel().also { viewModels.put("deckPicker", it) }
    }

    @After
    fun clearViewModels() {
        viewModels.clear()
    }

    @Test
    fun `filtering a stale deck list does not reopen the collection`() =
        runTest {
            val deckId = addDeck("Kept")
            viewModel.reloadDeckCounts().join()
            viewModel.flowOfDeckList.first { list -> list.data.any { it.did == deckId } }

            CollectionManager.ensureClosed()
            viewModel.updateDeckFilter("Kept")
            runCurrent()

            assertFalse(CollectionManager.isOpenUnsafe())
            assertEquals(FlattenedDeckList.empty, viewModel.flowOfDeckList.value)
        }

    /** Issue 20986: a display refresh must not reopen a collection held by another app. */
    @Test
    fun `deck list survives an external collection lock and can refresh afterwards`() =
        runTest {
            val kept = addDeck("Kept")
            addDeck("Other")
            viewModel.reloadDeckCounts().join()
            viewModel.flowOfDeckList.first { list -> list.data.any { it.did == kept } }
            val collectionPath = col.colDb.path

            CollectionManager.ensureClosed()
            Backend().use { conflictingBackend ->
                conflictingBackend.openCollection(collectionPath)
                assertFailsWith<CollectionLockedException> { CollectionManager.ensureOpen() }

                viewModel.updateDeckFilter("Kept")
                runCurrent()

                assertFalse(CollectionManager.isOpenUnsafe())
                assertEquals(FlattenedDeckList.empty, viewModel.flowOfDeckList.value)
            }

            CollectionManager.ensureOpen()
            viewModel.reloadDeckCounts().join()
            runCurrent()

            val refreshed = viewModel.flowOfDeckList.value
            assertEquals(listOf(kept), refreshed.data.map { it.did })
        }
}
