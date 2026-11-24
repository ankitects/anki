// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import com.ichi2.anki.common.android.R as CommonR

private fun string(resId: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

fun ComposeTestRule.showAnswerButton() = onNodeWithText(string(CommonR.string.show_answer))

// substring: the label is preceded by the next review time
fun ComposeTestRule.goodButton() = onNodeWithText(string(CommonR.string.ease_button_good), substring = true)

fun ComposeTestRule.clickShowAnswer() {
    showAnswerButton().performClick()
}

fun ComposeTestRule.awaitDisplayed(
    timeout: Duration = 10.seconds,
    node: ComposeTestRule.() -> SemanticsNodeInteraction,
): SemanticsNodeInteraction {
    waitUntil(timeout.inWholeMilliseconds) { runCatching { node().assertIsDisplayed() }.isSuccess }
    return node()
}
