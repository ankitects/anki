// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-mdpi")
class SwipeableSnackbarOutgoingRecaptureTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the same swipe dismisses when it is not recaptured`() {
        val host = SnackbarHostState()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme { SwipeableSnackbarHost(host) }
            LaunchedEffect(host) { host.showSnackbar("Swipe me", duration = SnackbarDuration.Indefinite) }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithText("Swipe me").performTouchInput {
            down(center)
            repeat(22) { moveBy(Offset(10.dp.toPx(), 0f), delayMillis = 100) }
            up()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertNull(host.currentSnackbarData, "Control swipe must dismiss the snackbar")
    }

    @Test
    fun `touching a dismissing snackbar keeps it until release`() {
        val host = SnackbarHostState()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme { SwipeableSnackbarHost(host) }
            LaunchedEffect(host) { host.showSnackbar("Swipe me", duration = SnackbarDuration.Indefinite) }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val message = compose.onNodeWithText("Swipe me")
        val originalCenter = message.fetchSemanticsNode().boundsInRoot.center
        message.performTouchInput {
            down(center)
            repeat(22) { moveBy(Offset(10.dp.toPx(), 0f), delayMillis = 100) }
            up()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val center = message.fetchSemanticsNode().boundsInRoot.center
        assertTrue(center.x > originalCenter.x + 100f, "Snackbar must still be moving away when touched")
        val root = compose.onRoot()
        root.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertNotNull(host.currentSnackbarData, "Snackbar was dismissed while held")
        val heldCenter = message.fetchSemanticsNode().boundsInRoot.center
        assertEquals(center.x, heldCenter.x, 1f, "Snackbar moved while held")
        root.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertNull(host.currentSnackbarData, "Releasing past halfway should dismiss the snackbar")
    }

    @Test
    fun `a recaptured snackbar can be dragged back and its action used`() {
        val host = SnackbarHostState()
        var result: SnackbarResult? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme { SwipeableSnackbarHost(host) }
            LaunchedEffect(host) {
                result = host.showSnackbar("Swipe me", actionLabel = "Undo", duration = SnackbarDuration.Indefinite)
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val message = compose.onNodeWithText("Swipe me")
        message.performTouchInput {
            down(center)
            repeat(22) { moveBy(Offset(10.dp.toPx(), 0f), delayMillis = 100) }
            up()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val center = message.fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput {
            down(center)
            repeat(16) { moveBy(Offset(-10.dp.toPx(), 0f), delayMillis = 100) }
            up()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertNotNull(host.currentSnackbarData)
        compose.onNodeWithText("Undo").performTouchInput { click() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(SnackbarResult.ActionPerformed, result)
    }
}
