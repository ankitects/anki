/*
 *  Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.FrameStyle
import com.ichi2.anki.settings.enums.ToolbarPosition
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
class StudyScreenScreenshotTest(
    private val config: TestConfig,
) : ScreenshotTest() {
    init {
        Prefs.isNewStudyScreenEnabled = true
        Prefs.toolbarPosition = config.toolbarPosition
        Prefs.showAnswerButtons = config.showAnswerButtons
        Prefs.frameStyle = config.frameStyle
    }

    @Test
    fun captureScreenshot() {
        ActivityScenario
            .launch<CardViewerActivity>(
                ReviewerFragment.getIntent(targetContext),
            ).use { scenario ->
                scenario.onActivity {
                    captureScreen(config.toString())
                }
            }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun data(): Collection<Array<TestConfig>> {
            // Comment out to isolate tests
            val positions = ToolbarPosition.entries
            // val positions = listOf(ToolbarPosition.TOP)

            val buttonStates = listOf(true, false)
            // val buttonStates = listOf(true)

            val styles = FrameStyle.entries
            // val styles = listOf(FrameStyle.BOX)

            val configs = mutableListOf<Array<TestConfig>>()
            for (pos in positions) {
                for (btn in buttonStates) {
                    for (style in styles) {
                        val config =
                            TestConfig(
                                pos,
                                btn,
                                style,
                            )
                        configs.add(arrayOf(config))
                    }
                }
            }
            return configs
        }
    }

    /**
     * Represents a single snapshot configuration.
     * The toString() provides the name shown in the JUnit test results.
     */
    class TestConfig(
        val toolbarPosition: ToolbarPosition,
        val showAnswerButtons: Boolean,
        val frameStyle: FrameStyle,
    ) {
        override fun toString(): String =
            "toolbar=${toolbarPosition.name}_" +
                "frameStyle=${frameStyle.name}_buttons=$showAnswerButtons"
    }
}
