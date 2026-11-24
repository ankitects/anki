// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Intent
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.SingleFragmentScreenshotTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test

/**
 * Screenshot tests for [CongratsPage]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.pages.CongratsPageScreenshotTest"`
 */
class CongratsPageScreenshotTest : SingleFragmentScreenshotTest() {
    override fun buildIntent(): Intent = CongratsPage.getIntent(targetContext)

    @Test
    fun `standard deck`() =
        runTest {
            addBasicNote()
            withCol { sched.answerCard(checkNotNull(sched.card), Rating.EASY) }

            withScreen {
                advanceUntilIdle()
                advanceRobolectricLooper()
                captureScreen("standardDeck")
            }
        }

    @Test
    fun `filtered deck`() =
        runTest {
            addBasicNote()
            addDynamicDeck("Filtered", "")
            withCol { sched.answerCard(checkNotNull(sched.card), Rating.EASY) }

            withScreen {
                advanceUntilIdle()
                advanceRobolectricLooper()
                captureScreen("filteredDeck")
            }
        }
}
