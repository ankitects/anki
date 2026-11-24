// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.ui.windows.reviewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import anki.scheduler.CardAnswer.Rating
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.RobolectricTest
import com.ichi2.compose.theme.AnkiDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestParameterInjector
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.ichi2.anki.common.android.R as CommonR

@RunWith(RobolectricTestParameterInjector::class)
class AnswerAreaTest : RobolectricTest() {
    @get:Rule
    val composeRule = createComposeRule()

    private val showAnswer get() = targetContext.getString(CommonR.string.show_answer)
    private val again get() = targetContext.getString(CommonR.string.ease_button_again)
    private val hard get() = targetContext.getString(CommonR.string.ease_button_hard)
    private val good get() = targetContext.getString(CommonR.string.ease_button_good)
    private val easy get() = targetContext.getString(CommonR.string.ease_button_easy)

    private fun setAnswerArea(
        isAnswerShown: Boolean,
        nextTimes: AnswerButtonsNextTime? = null,
        showHardAndEasyButtons: Boolean = true,
        onRatingClicked: (Rating) -> Unit = {},
        onShowAnswerClicked: () -> Unit = {},
    ) {
        composeRule.setContent {
            AnkiDroidTheme {
                AnswerArea(
                    isAnswerShown = isAnswerShown,
                    nextTimes = nextTimes,
                    onRatingClicked = onRatingClicked,
                    onShowAnswerClicked = onShowAnswerClicked,
                    showHardAndEasyButtons = showHardAndEasyButtons,
                )
            }
        }
    }

    @Test
    fun `clicking show answer button calls onShowAnswerClicked`() {
        var clicks = 0
        setAnswerArea(isAnswerShown = false, onShowAnswerClicked = { clicks++ })

        composeRule.onNodeWithText(showAnswer).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `clicking rating buttons calls onRatingClicked with correct rating`() {
        val ratings = mutableListOf<Rating>()
        setAnswerArea(isAnswerShown = true, onRatingClicked = { ratings += it })

        listOf(again, hard, good, easy).forEach { composeRule.onNodeWithText(it).performClick() }

        assertEquals(listOf(Rating.AGAIN, Rating.HARD, Rating.GOOD, Rating.EASY), ratings)
    }

    @Test
    fun `showing the answer shows rating buttons and hides show answer button`() {
        setAnswerArea(isAnswerShown = true)

        composeRule.onNodeWithText(showAnswer).assertIsNotDisplayed()
        listOf(again, hard, good, easy).forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun `showing the question hides rating buttons and shows show answer button`() {
        setAnswerArea(isAnswerShown = false)

        composeRule.onNodeWithText(showAnswer).assertIsDisplayed()
        listOf(again, hard, good, easy).forEach { composeRule.onNodeWithText(it).assertIsNotDisplayed() }
    }

    @Test
    fun `toggling the answer state swaps the visible buttons`() {
        var isAnswerShown by mutableStateOf(false)
        composeRule.setContent {
            AnkiDroidTheme {
                AnswerArea(
                    isAnswerShown = isAnswerShown,
                    nextTimes = null,
                    onRatingClicked = {},
                    onShowAnswerClicked = {},
                )
            }
        }

        isAnswerShown = true

        composeRule.onNodeWithText(showAnswer).assertIsNotDisplayed()
        composeRule.onNodeWithText(good).assertIsDisplayed()
    }

    @Test
    fun `hard and easy buttons can be hidden`() {
        setAnswerArea(isAnswerShown = true, showHardAndEasyButtons = false)

        composeRule.onNodeWithText(hard).assertDoesNotExist()
        composeRule.onNodeWithText(easy).assertDoesNotExist()
        composeRule.onNodeWithText(again).assertIsDisplayed()
        composeRule.onNodeWithText(good).assertIsDisplayed()
    }

    @Test
    fun `rating buttons have equal widths`(
        @TestParameter showHardAndEasyButtons: Boolean,
    ) {
        setAnswerArea(isAnswerShown = true, showHardAndEasyButtons = showHardAndEasyButtons)

        val labels = if (showHardAndEasyButtons) listOf(again, hard, good, easy) else listOf(again, good)
        val widths =
            labels.map {
                composeRule
                    .onNodeWithText(it)
                    .fetchSemanticsNode()
                    .size.width
            }
        // Can differ by one pixel when the width is not evenly divisible due to rounding.
        assertTrue(widths.max() - widths.min() <= 1, "Rating button widths: $widths")
    }

    @Test
    fun `next times are shown above the ease names`() {
        val nextTimes = AnswerButtonsNextTime(again = "<1m", hard = "6m", good = "10m", easy = "4d")
        setAnswerArea(isAnswerShown = true, nextTimes = nextTimes)

        composeRule.onNodeWithText("<1m\n$again").assertIsDisplayed()
        composeRule.onNodeWithText("6m\n$hard").assertIsDisplayed()
        composeRule.onNodeWithText("10m\n$good").assertIsDisplayed()
        composeRule.onNodeWithText("4d\n$easy").assertIsDisplayed()
    }

    @Test
    fun `buttons height percentage scales the answer area height`() {
        var percentage by mutableStateOf(100)
        composeRule.setContent {
            AnkiDroidTheme {
                AnswerArea(
                    isAnswerShown = true,
                    nextTimes = null,
                    onRatingClicked = {},
                    onShowAnswerClicked = {},
                    modifier = Modifier.testTag("answer_area"),
                    buttonsHeightPercentage = percentage,
                )
            }
        }
        val node = composeRule.onNodeWithTag("answer_area")
        val defaultHeight = node.fetchSemanticsNode().size.height
        assertTrue(defaultHeight > 0)

        percentage = 200

        assertEquals(defaultHeight * 2, node.fetchSemanticsNode().size.height)
    }
}
