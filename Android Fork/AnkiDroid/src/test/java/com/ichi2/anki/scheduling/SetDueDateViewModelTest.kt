// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.scheduling

import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.sched.SetDueDateDays
import com.ichi2.anki.scheduling.SetDueDateViewModel.DateRange
import com.ichi2.anki.scheduling.SetDueDateViewModel.Tab
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.common.assertThrows
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetDueDateViewModelTest : JvmTest() {
    @Test
    fun `initial values`() =
        runViewModelTest(cardIds = listOf(1, 2)) {
            assertThat("card count", cardCount, equalTo(2))
            assertThat("is not valid", !isValid)
            assertThat("initial tab is single day", currentTab, equalTo(Tab.SINGLE_DAY))
        }

    @Test
    fun `submission waits for scheduler initialization`() =
        runTest {
            val viewModel = SetDueDateViewModel()
            viewModel.nextSingleDayDueDate = 3

            assertFalse(viewModel.isValid, "valid input cannot be submitted before the scheduler setting is known")
            assertThat(viewModel.calculateDaysParameter(), equalTo(null))
            assertThat("keyboard submission is also guarded", viewModel.updateDueDateAsync().await(), equalTo(null))

            col.config.set("fsrs", true)
            viewModel.init(cardIds = listOf(1, 2))
            advanceUntilIdle()

            assertTrue(viewModel.isValid, "existing input becomes valid once the scheduler setting is known")
            assertThat(viewModel.calculateDaysParameter(), equalTo(SetDueDateDays("3!")))
        }

    @Test
    fun `single day validation`() =
        runViewModelTest {
            fun canSaveWithValue(
                input: NumberOfDaysInFuture?,
                expected: Boolean,
            ) {
                nextSingleDayDueDate = input
                assertThat("$input", isValid == expected, equalTo(true))
            }
            assertThat("is not valid", !isValid)
            canSaveWithValue(-1, false)
            canSaveWithValue(0, true)
            canSaveWithValue(null, false)
            canSaveWithValue(1, true)
        }

    @Test
    fun `date range validation`() =
        runViewModelTest {
            fun canSaveWithValue(
                start: NumberOfDaysInFuture?,
                end: NumberOfDaysInFuture?,
                expected: Boolean,
                message: String? = null,
            ) {
                setNextDateRangeStart(start)
                setNextDateRangeEnd(end)
                assertThat(message ?: "${DateRange(start, end)}", isValid == expected, equalTo(true))
            }
            currentTab = Tab.DATE_RANGE
            assertThat("initially not valid", !isValid)
            canSaveWithValue(-1, -1, false)
            canSaveWithValue(-1, 0, false)
            canSaveWithValue(0, -1, false)

            canSaveWithValue(0, 0, true)

            canSaveWithValue(null, null, false)
            canSaveWithValue(null, 1, false)
            canSaveWithValue(1, null, false)

            canSaveWithValue(2, 3, true)
            canSaveWithValue(3, 2, false, "start must be <= end")
        }

    @Test
    fun `test string output`() =
        runViewModelTest {
            fun eq(s: String) = equalTo(SetDueDateDays(s))
            currentTab = Tab.SINGLE_DAY
            assertThat(calculateDaysParameter(), equalTo(null))

            nextSingleDayDueDate = 1
            assertThat(calculateDaysParameter(), eq("1"))

            updateIntervalToMatchDueDate = true
            assertThat(calculateDaysParameter(), eq("1!"))

            updateIntervalToMatchDueDate = false
            currentTab = Tab.DATE_RANGE
            assertThat(calculateDaysParameter(), equalTo(null))

            setNextDateRangeStart(0)
            setNextDateRangeEnd(0)
            assertThat(calculateDaysParameter(), eq("0"))

            setNextDateRangeEnd(1)
            assertThat(calculateDaysParameter(), eq("0-1"))

            updateIntervalToMatchDueDate = true
            assertThat(calculateDaysParameter(), eq("0-1!"))
        }

    @Test
    fun `if FSRS is enabled updateIntervalToMatchDueDate may not be set to false`() {
        runViewModelTest(fsrsEnabled = true) {
            // the value can be set to true
            updateIntervalToMatchDueDate = true

            assertFalse(
                canSetUpdateIntervalToMatchDueDate,
                "canSetUpdateIntervalToMatchDueDate when FSRS enabled",
            )

            val ex =
                assertThrows<UnsupportedOperationException>("due date mismatch & FSRS enabled") {
                    updateIntervalToMatchDueDate = false
                }
            assertThat(ex.message, equalTo("due date must match interval if using FSRS"))
        }

        runViewModelTest(fsrsEnabled = false) {
            assertTrue(
                canSetUpdateIntervalToMatchDueDate,
                "canSetUpdateIntervalToMatchDueDate and no FSRS",
            )
        }
    }

    @Test
    fun `updateIntervalToMatchDueDate default value, given scheduler`() {
        runViewModelTest(fsrsEnabled = true) {
            assertThat(
                "updateIntervalToMatchDueDate default if FSRS",
                updateIntervalToMatchDueDate,
                equalTo(true),
            )
        }

        runViewModelTest(fsrsEnabled = false) {
            assertThat(
                "updateIntervalToMatchDueDate default if no FSRS",
                updateIntervalToMatchDueDate,
                equalTo(false),
            )
        }
    }

    @Test
    fun `currentInterval is null when multiple cards are selected`() {
        runViewModelTest(cardIds = listOf(1, 2)) {
            assertThat("currentInterval should be null", currentInterval.value, equalTo(null))
        }
    }

    private fun runViewModelTest(
        cardIds: List<CardId> = listOf(1, 2, 3),
        fsrsEnabled: Boolean = false,
        testBody: suspend SetDueDateViewModel.() -> Unit,
    ) = runTest {
        col.config.set("fsrs", fsrsEnabled)
        val viewModel = SetDueDateViewModel()
        viewModel.init(cardIds)
        advanceUntilIdle()
        testBody(viewModel)
    }
}

val SetDueDateViewModel.isValid: Boolean get() = isValidFlow.value
