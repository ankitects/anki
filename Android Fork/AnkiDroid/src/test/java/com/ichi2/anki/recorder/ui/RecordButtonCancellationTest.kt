// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.recorder.ui

import android.view.ViewConfiguration
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class, sdk = [36])
class RecordButtonCancellationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val status = mutableStateOf(RecorderStatus.READY)
    private var releases = 0

    @Test
    fun `cancelling a held press ends recording`() {
        showButton()
        composeRule.onNodeWithTag("record").performTouchInput { down(center) }
        composeRule.runOnIdle { assertEquals(RecorderStatus.RECORDING, status.value) }
        composeRule.onNodeWithTag("record").performTouchInput {
            advanceEventTime(ViewConfiguration.getLongPressTimeout().toLong() + 100)
            cancel()
        }

        composeRule.runOnIdle {
            assertEquals(1, releases, "cancellation must finish the held recording")
            assertEquals(RecorderStatus.RECORDED, status.value)
        }
    }

    @Test
    fun `releasing a held press ends recording`() {
        showButton()
        composeRule.onNodeWithTag("record").performTouchInput {
            down(center)
            advanceEventTime(ViewConfiguration.getLongPressTimeout().toLong() + 100)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(1, releases)
            assertEquals(RecorderStatus.RECORDED, status.value)
        }
    }

    @Test
    fun `a short tap leaves recording active`() {
        showButton()
        composeRule.onNodeWithTag("record").performTouchInput {
            down(center)
            advanceEventTime(50)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(0, releases)
            assertEquals(RecorderStatus.RECORDING, status.value)
        }
    }

    private fun showButton() {
        composeRule.setContent {
            RecordButton(
                status = status.value,
                onToggle = {
                    status.value = RecorderStatus.RECORDING
                    true
                },
                onHoldRelease = {
                    releases++
                    status.value = RecorderStatus.RECORDED
                },
                modifier = Modifier.testTag("record"),
            )
        }
    }
}
