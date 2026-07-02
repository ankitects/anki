/*
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.notetype

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.exception.CombinedException
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.removeNotetype
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.hasProperty
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ManageNoteTypesViewModelTest : RobolectricTest() {
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

        /**
         * The current number of available [anki.notetypes.StockNotetype] found in the [Collection].
         */
        private const val STOCK_NOTE_TYPES_COUNT = 6
    }
}
