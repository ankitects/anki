// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer

import com.ichi2.anki.ComposeScreenshotTest
import org.junit.Test

/**
 * Record: `./gradlew :AnkiDroid:recordRoborazziPlayDebug -Pscreenshot --tests '*.AnswerAreaScreenshotTest'`
 * Verify: `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests '*.AnswerAreaScreenshotTest'`
 */
class AnswerAreaScreenshotTest : ComposeScreenshotTest() {
    private val nextTimes = AnswerButtonsNextTime(again = "<1m", hard = "<6m", good = "<10m", easy = "4d")

    @Test
    fun question() = captureAnswerArea("question", isAnswerShown = false)

    @Test
    fun answer() = captureAnswerArea("answer", isAnswerShown = true)

    @Test
    fun questionWithThemeColors() = captureAnswerArea("question_theme_colors", isAnswerShown = false, useThemeColors = true)

    @Test
    fun answerWithThemeColors() = captureAnswerArea("answer_theme_colors", isAnswerShown = true, useThemeColors = true)

    private fun captureAnswerArea(
        name: String,
        isAnswerShown: Boolean,
        useThemeColors: Boolean = false,
    ) = capture(name) {
        AnswerArea(
            isAnswerShown = isAnswerShown,
            nextTimes = nextTimes,
            onRatingClicked = {},
            onShowAnswerClicked = {},
            useThemeColors = useThemeColors,
        )
    }
}
