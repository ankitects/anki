// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.notetype

import anki.notetypes.StockNotetype
import app.cash.turbine.test
import com.ichi2.anki.exception.CombinedException
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.getNotetypeNameIdUseCount
import com.ichi2.anki.libanki.removeNotetype
import com.ichi2.anki.notetype.ManageNoteTypesState.UserMessage
import com.ichi2.anki.progress.ViewModelProgress
import com.ichi2.testutils.JvmTest
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.setMain
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.hasProperty
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import org.junit.jupiter.api.assertInstanceOf
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManageNoteTypesViewModelTest : JvmTest() {
    @Test
    fun `multiple selection with one entry works as expected`() =
        runTest {
            addStandardNoteType(TEST_NAME_1, arrayOf("front", "back"), "", "")

            val viewModel = ManageNoteTypesViewModel()

            assertTrue(TEST_NAME_1 in viewModel.noteTypes.map { it.name })
            assertFalse(viewModel.currentMultiModeStatus)
            // check that the item long clicked is selected and we are now in multi select mode
            viewModel.onItemLongClick(viewModel.noteTypeForName(TEST_NAME_1))
            assertTrue(viewModel.noteTypeForName(TEST_NAME_1).isSelected)
            assertTrue(viewModel.currentMultiModeStatus)
            // deselecting by clicking the same row
            viewModel.onItemLongClick(viewModel.noteTypeForName(TEST_NAME_1))
            assertThat(viewModel.noteTypes, everyItem(isNotSelected()))
            assertFalse(viewModel.currentMultiModeStatus)
        }

    @Test
    fun `clearing selection works as expected`() =
        runTest {
            addStandardNoteType(TEST_NAME_1, arrayOf("front", "back"), "", "")
            addStandardNoteType(TEST_NAME_2, arrayOf("front", "back"), "", "")

            val viewModel = ManageNoteTypesViewModel()

            viewModel.onItemLongClick(viewModel.noteTypeForName(TEST_NAME_1))
            assertTrue(viewModel.noteTypeForName(TEST_NAME_1).isSelected)
            viewModel.onItemClick(viewModel.noteTypeForName(TEST_NAME_2))
            assertTrue(viewModel.noteTypeForName(TEST_NAME_2).isSelected)
            viewModel.clearSelection()
            assertThat(viewModel.noteTypes, everyItem(isNotSelected()))
            assertFalse(viewModel.currentMultiModeStatus)
        }

    @Test
    fun `deleting multiple selected note types works as expected`() =
        runTest {
            runTest {
                addStandardNoteType(TEST_NAME_1, arrayOf("front", "back"), "", "")
                addStandardNoteType(TEST_NAME_2, arrayOf("front", "back"), "", "")

                val viewModel = ManageNoteTypesViewModel()

                viewModel.onItemLongClick(viewModel.noteTypeForName(TEST_NAME_1))
                viewModel.onItemClick(viewModel.noteTypeForName(TEST_NAME_2))
                viewModel.deleteSelectedNoteTypes()
                assertThat(viewModel.noteTypes, hasSize(STOCK_NOTE_TYPES_COUNT))
                assertThat(
                    viewModel.noteTypes.map { it.name },
                    not(hasItems(TEST_NAME_1, TEST_NAME_2)),
                )
            }
        }

    @Test
    fun `checking items in multiple selection works as expected`() =
        runTest {
            addStandardNoteType(TEST_NAME_1, arrayOf("front", "back"), "", "")
            addStandardNoteType(TEST_NAME_2, arrayOf("front", "back"), "", "")

            val viewModel = ManageNoteTypesViewModel()

            assertThat(viewModel.noteTypes.map { it.name }, hasItems(TEST_NAME_1, TEST_NAME_2))

            viewModel.onItemLongClick(viewModel.noteTypeForName(TEST_NAME_1))
            viewModel.onItemChecked(viewModel.noteTypeForName(TEST_NAME_2), true)

            // deselecting the first one by clicking the CheckBox, test note type two should still be selected
            viewModel.onItemChecked(viewModel.noteTypeForName(TEST_NAME_1), false)
            assertTrue(viewModel.noteTypeForName(TEST_NAME_2).isSelected)
            assertTrue(viewModel.currentMultiModeStatus)
            assertThat(
                viewModel.noteTypes.filterNot { it.name == TEST_NAME_2 },
                everyItem(isNotSelected()),
            )
            // deselect all
            viewModel.onItemChecked(viewModel.noteTypeForName(TEST_NAME_2), false)
            assertThat(viewModel.noteTypes, everyItem(isNotSelected()))
            assertFalse(viewModel.currentMultiModeStatus)
        }

    @Test
    fun `filtering preserves previous selection`() =
        runTest {
            val testName1 = "A_XYZ"
            val testName2 = "B_QWE"
            addStandardNoteType(testName1, arrayOf("front", "back"), "", "")
            addStandardNoteType(testName2, arrayOf("front", "back"), "", "")

            val viewModel = ManageNoteTypesViewModel()

            // select the first one
            viewModel.onItemLongClick(viewModel.noteTypeForName(testName1))
            // filter so only the second is shown
            viewModel.filter(testName2)
            val currentlyDisplayed = viewModel.noteTypes.filter { it.shouldBeDisplayed }
            assertThat(currentlyDisplayed, hasSize(1))
            assertEquals(testName2, currentlyDisplayed[0].name)
            // the previous selection should still exist
            assertTrue(viewModel.noteTypeForName(testName1).isSelected)
            assertTrue(viewModel.currentMultiModeStatus)
            // select the second note type
            viewModel.onItemClick(viewModel.noteTypeForName(testName2))
            // the two items are now selected
            assertThat(viewModel.selectedNoteTypes, hasSize(2))
            assertContentEquals(
                listOf(testName1, testName2),
                viewModel.selectedNoteTypes.map { it.name }.sorted(),
            )
            // clearing the query, both entries should still be selected
            viewModel.filter("")
            assertThat(viewModel.selectedNoteTypes, hasSize(2))
            assertTrue(viewModel.noteTypeForName(testName1).isSelected)
            assertTrue(viewModel.noteTypeForName(testName2).isSelected)
        }

    @Test
    fun `filtering is case insensitive`() =
        runTest {
            val testName = "MixedCaseName"
            addStandardNoteType(testName, arrayOf("front", "back"), "", "")
            val viewModel = ManageNoteTypesViewModel()
            viewModel.filter("mixedcasename")
            val currentlyDisplayed =
                viewModel.state.value.noteTypes
                    .filter { it.shouldBeDisplayed }
            assertThat(currentlyDisplayed, hasSize(1))
            assertThat(currentlyDisplayed.map { it.name }, hasItems(testName))
        }

    @Test
    fun `removal failure in multiple selection returns expected exception`() =
        runTest {
            mockkStatic(Collection::removeNotetype)
            addStandardNoteType(TEST_NAME_1, arrayOf("front", "back"), "", "")
            addStandardNoteType(TEST_NAME_2, arrayOf("front", "back"), "", "")
            every { any<Collection>().removeNotetype(any()) } throws
                IllegalStateException(TEST_EXCEPTION_MESSAGE)

            val viewModel = ManageNoteTypesViewModel()

            viewModel.onItemLongClick(viewModel.noteTypeForName(TEST_NAME_1))
            viewModel.onItemClick(viewModel.noteTypeForName(TEST_NAME_2))
            viewModel.deleteSelectedNoteTypes()
            val currentError = viewModel.state.value.error
            assertNotNull(currentError)
            // the wrapper exception will be an IllegalStateException
            assertInstanceOf<CombinedException>(currentError.source)
            // we declared the backend method to throw an IllegalStateException
            assertEquals(
                """
                $TEST_NAME_1 - java.lang.IllegalStateException: $TEST_EXCEPTION_MESSAGE
                $TEST_NAME_2 - java.lang.IllegalStateException: $TEST_EXCEPTION_MESSAGE
                """.trimIndent(),
                currentError.source.message,
            )
            // viewModel should still have the items
            assertThat(viewModel.noteTypes.map { it.name }, hasItems(TEST_NAME_1, TEST_NAME_2))
            unmockkStatic(Collection::removeNotetype)
        }

    @Test
    fun `adding a stock note type adds it to the list`() =
        runTest {
            val viewModel = ManageNoteTypesViewModel()

            viewModel.addStockNoteType(TEST_NAME_1, StockNotetype.Kind.KIND_CLOZE).await()

            assertTrue(TEST_NAME_1 in viewModel.noteTypes.map { it.name })
            assertTrue(col.notetypes.byName(TEST_NAME_1)!!.isCloze)
        }

    @Test
    fun `cloning a note type copies its fields`() =
        runTest {
            addStandardNoteType(TEST_NAME_1, arrayOf("front", "back"), "{{front}}", "{{back}}")
            val viewModel = ManageNoteTypesViewModel()

            viewModel.cloneNoteType(TEST_NAME_2, viewModel.noteTypeForName(TEST_NAME_1).id).await()

            assertThat(viewModel.noteTypes.map { it.name }, hasItems(TEST_NAME_1, TEST_NAME_2))
            assertEquals(listOf("front", "back"), col.notetypes.byName(TEST_NAME_2)!!.fieldsNames)
        }

    @Test
    fun `cloning a missing note type fails without adding it`() =
        runTest {
            val viewModel = ManageNoteTypesViewModel()

            assertFailsWith<BackendNotFoundException> { viewModel.cloneNoteType(TEST_NAME_1, MISSING_NOTE_TYPE_ID).await() }

            assertNull(col.notetypes.byName(TEST_NAME_1))
        }

    @Test
    fun `addStockNoteType reports progress`() =
        runProgressTest {
            assertProgressAround { addStockNoteType(TEST_NAME_2, StockNotetype.Kind.KIND_BASIC) }
        }

    @Test
    fun `cloneNoteType reports progress`() =
        runProgressTest {
            assertProgressAround { cloneNoteType(TEST_NAME_2, noteTypeForName(TEST_NAME_1).id) }
        }

    @Test
    fun `loadAddNoteTypeOptions reports progress`() =
        runProgressTest {
            assertProgressAround {
                val (options, names) = loadAddNoteTypeOptions()
                assertThat(names, hasItems(TEST_NAME_1))
                assertEquals(names, options.filterNot { it.isStandard }.map { it.name })
                assertTrue(options.any { it.isStandard })
            }
        }

    @Test
    fun `refreshNoteTypes reports progress`() =
        runProgressTest {
            assertProgressAround { refreshNoteTypes() }
        }

    @Test
    fun `rename reports progress`() =
        runProgressTest {
            assertProgressAround { rename(noteTypeForName(TEST_NAME_1).id, TEST_NAME_2) }
        }

    @Test
    fun `delete reports progress`() =
        runProgressTest {
            assertProgressAround { delete(noteTypeForName(TEST_NAME_1).id) }
        }

    @Test
    fun `deleteSelectedNoteTypes reports progress`() =
        runProgressTest {
            onItemLongClick(noteTypeForName(TEST_NAME_1))

            assertProgressAround { deleteSelectedNoteTypes() }
        }

    @Test
    fun `deleting the last note type reports no progress`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            col.getNotetypeNameIdUseCount().drop(1).forEach { col.removeNotetype(it.id) }
            val viewModel = ManageNoteTypesViewModel()
            advanceUntilIdle()

            viewModel.progressManager.progress.test {
                assertIs<ViewModelProgress.Idle>(awaitItem())
                viewModel.delete(viewModel.noteTypes.single().id)
                advanceUntilIdle()
                expectNoEvents()
            }
            assertEquals(UserMessage.DeletingLastModel, viewModel.state.value.message)
        }

    private fun runProgressTest(testBody: suspend ManageNoteTypesViewModel.() -> Unit) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            addStandardNoteType(TEST_NAME_1, arrayOf("front", "back"), "{{front}}", "{{back}}")
            val viewModel = ManageNoteTypesViewModel()
            advanceUntilIdle()
            viewModel.testBody()
        }

    private suspend fun ManageNoteTypesViewModel.assertProgressAround(op: suspend () -> Unit) {
        progressManager.progress.test {
            assertIs<ViewModelProgress.Idle>(awaitItem())
            op()
            assertIs<ViewModelProgress.Active<Nothing>>(awaitItem())
            assertIs<ViewModelProgress.Idle>(awaitItem())
            expectNoEvents()
        }
    }

    /** Returns the current list of [NoteTypeItemState] */
    private val ManageNoteTypesViewModel.noteTypes: List<NoteTypeItemState>
        get() = state.value.noteTypes

    /** Returns the current multi selecte mode status */
    private val ManageNoteTypesViewModel.currentMultiModeStatus: Boolean
        get() = state.value.noteTypes.multiSelectModeStatus

    /** Returns the [NoteTypeItemState] that has the provided [name] */
    private fun ManageNoteTypesViewModel.noteTypeForName(name: String): NoteTypeItemState =
        noteTypes.find { it.name == name }
            ?: throw IllegalStateException("Unable to find note type with name: $name")

    /**
     * Matcher that checks for the [NoteTypeItemState.isSelected] being false.
     * Note: the actual property name used here is "selected" to match kotlin's "isSelected" property.
     */
    private fun isNotSelected() = hasProperty<NoteTypeItemState>("selected", equalTo(false))

    companion object {
        private const val TEST_NAME_1 = "BasicTestNoteType1"
        private const val TEST_NAME_2 = "BasicTestNoteType2"

        /** Exception message used when mocking backend calls that should fail */
        private const val TEST_EXCEPTION_MESSAGE = "Failure: can't delete"

        private const val MISSING_NOTE_TYPE_ID = 1L

        /**
         * The current number of available [anki.notetypes.StockNotetype] found in the [Collection].
         */
        private const val STOCK_NOTE_TYPES_COUNT = 6
    }
}
