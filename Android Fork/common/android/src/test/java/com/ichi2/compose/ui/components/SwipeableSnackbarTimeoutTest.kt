// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SwipeableSnackbarTimeoutTest {
    @get:Rule
    val compose = createComposeRule()
    private val host = SnackbarHostState()

    @Test
    fun `the timeout waits for a partial swipe to settle`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme { SwipeableSnackbarHost(host) }
            LaunchedEffect(host) { host.showSnackbar("Return me", duration = SnackbarDuration.Short) }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val message = compose.onNodeWithText("Return me")
        val restingLeft = message.getUnclippedBoundsInRoot().left

        message.performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(18.dp.toPx(), 0f), delayMillis = 50) }
            up()
        }
        val releasedAt = compose.mainClock.currentTime
        var frames = 0
        do {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            frames++
        } while (host.currentSnackbarData != null && message.getUnclippedBoundsInRoot().left != restingLeft && frames < 120)
        val settledAfter = compose.mainClock.currentTime - releasedAt

        // View snackbars restart their full timeout only once the drag helper is idle again.
        compose.mainClock.advanceTimeBy(1_400)
        assertEquals("Return me", host.currentSnackbarData?.visuals?.message, "dismissed; settled ${settledAfter}ms after release")
        // Rounded bounds can reach their resting position before the spring is completely idle.
        compose.mainClock.advanceTimeBy(1_000)
        assertNull(host.currentSnackbarData)
    }
}
