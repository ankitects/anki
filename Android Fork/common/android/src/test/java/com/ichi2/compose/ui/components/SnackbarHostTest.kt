// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SnackbarHostTest {
    @get:Rule
    val compose = createComposeRule()
    private val host = SnackbarHostState()

    @Test
    fun `holding a swipeable snackbar pauses dismissal until release`() {
        show(swipeable = true) { showSnackbar("Hold me") }
        compose.mainClock.advanceTimeBy(500)
        val message = compose.onNodeWithText("Hold me")
        message.performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(10_000)
        assertEquals("Hold me", host.currentSnackbarData?.visuals?.message)
        message.performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_300)
        assertEquals("Hold me", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(300)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `touch just before the timeout restarts it on release`() {
        val timeout = 1_508L
        var timerStartedAt = 0L
        val manager =
            object : AccessibilityManager {
                override fun calculateRecommendedTimeoutMillis(
                    originalTimeoutMillis: Long,
                    containsIcons: Boolean,
                    containsText: Boolean,
                    containsControls: Boolean,
                ): Long {
                    timerStartedAt = compose.mainClock.currentTime
                    return timeout
                }
            }
        show(manager, swipeable = true) { showSnackbar("Hold me", duration = SnackbarDuration.Short) }
        val message = compose.onNodeWithText("Hold me")
        val deadline = timerStartedAt + timeout
        compose.mainClock.advanceTimeBy(deadline - compose.mainClock.currentTime - 1, ignoreFrameDuration = true)
        message.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(32)
        assertEquals("Hold me", host.currentSnackbarData?.visuals?.message)

        message.performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_300)
        assertEquals("Hold me", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(300)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `cancelled touch resumes the snackbar timeout`() {
        show(swipeable = true) { showSnackbar("Cancel touch", duration = SnackbarDuration.Long) }
        val message = compose.onNodeWithText("Cancel touch")
        message.performTouchInput { down(center) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(5_000)
        assertEquals("Cancel touch", host.currentSnackbarData?.visuals?.message)
        message.performTouchInput { cancel() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_500)
        assertEquals("Cancel touch", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(350)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `slight inward drift does not undo a swipe past halfway`() {
        show(swipeable = true) { showSnackbar("Swipe me", duration = SnackbarDuration.Indefinite) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Swipe me").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(24.dp.toPx(), 0f), delayMillis = 50) }
            repeat(8) { moveBy(Offset(-0.4.dp.toPx(), 0f), delayMillis = 16) }
            up()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()

        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `short messages last 1500 milliseconds`() {
        show { showSnackbar("Short") }
        compose.mainClock.advanceTimeBy(1_300)
        assertEquals("Short", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(250)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `long messages last 2750 milliseconds`() {
        show { showSnackbar("Long", duration = SnackbarDuration.Long) }
        compose.mainClock.advanceTimeBy(2_550)
        assertEquals("Long", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(250)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `short messages last 1500 milliseconds with Android accessibility manager`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme { SnackbarHost(host) }
            LaunchedEffect(host) { host.showSnackbar("Short") }
        }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_300)
        assertEquals("Short", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(250)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `accessibility can extend the View duration beyond the Compose default`() {
        val manager = RecordingAccessibilityManager()
        show(manager) { showSnackbar("Extended", actionLabel = "Undo", duration = SnackbarDuration.Short) }
        assertEquals(listOf(-1L), manager.requestedTimeouts)
        assertTrue(manager.containsControls)
        compose.mainClock.advanceTimeBy(5_000)
        assertEquals("Extended", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(1_000)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `accessibility extends long messages too`() {
        val manager = RecordingAccessibilityManager()
        show(manager) { showSnackbar("Extended long", duration = SnackbarDuration.Long) }
        assertEquals(listOf(0L), manager.requestedTimeouts)
        compose.mainClock.advanceTimeBy(5_000)
        assertEquals("Extended long", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(1_000)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `actions stay indefinitely and queued messages get their own timeout`() {
        val results = mutableListOf<SnackbarResult>()
        show(RecordingAccessibilityManager()) {
            results.add(showSnackbar("First", actionLabel = "Undo"))
            results.add(showSnackbar("Second"))
        }
        compose.mainClock.advanceTimeBy(60_000)
        assertEquals("First", host.currentSnackbarData?.visuals?.message)
        compose.onNodeWithText("Undo").performClick()
        compose.mainClock.advanceTimeBy(64)
        assertEquals(listOf(SnackbarResult.ActionPerformed), results)
        assertEquals("Second", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(5_000)
        assertEquals("Second", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(1_000)
        assertNull(host.currentSnackbarData)
        assertEquals(listOf(SnackbarResult.ActionPerformed, SnackbarResult.Dismissed), results)
    }

    @Test
    fun `zero accessibility recommendation preserves the short duration`() {
        show(RecordingAccessibilityManager(0L)) { showSnackbar("Short") }
        compose.mainClock.advanceTimeBy(1_300)
        assertEquals("Short", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(250)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `zero accessibility recommendation preserves the long duration`() {
        show(RecordingAccessibilityManager(0L)) { showSnackbar("Long", duration = SnackbarDuration.Long) }
        compose.mainClock.advanceTimeBy(2_550)
        assertEquals("Long", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(250)
        assertNull(host.currentSnackbarData)
    }

    @Test
    fun `positive accessibility recommendations are used directly`() {
        show(RecordingAccessibilityManager(1_000L)) { showSnackbar("Recommended") }
        compose.mainClock.advanceTimeBy(800)
        assertEquals("Recommended", host.currentSnackbarData?.visuals?.message)
        compose.mainClock.advanceTimeBy(250)
        assertNull(host.currentSnackbarData)
    }

    private fun show(
        accessibilityManager: AccessibilityManager? = null,
        swipeable: Boolean = false,
        messages: suspend SnackbarHostState.() -> Unit,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides accessibilityManager) {
                MaterialTheme {
                    if (swipeable) SwipeableSnackbarHost(host) else SnackbarHost(host)
                }
            }
            LaunchedEffect(host) { host.messages() }
        }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
    }

    private class RecordingAccessibilityManager(
        private val recommendedTimeoutMillis: Long = 6_000L,
    ) : AccessibilityManager {
        val requestedTimeouts = mutableListOf<Long>()
        var containsControls = false

        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean,
        ): Long {
            requestedTimeouts.add(originalTimeoutMillis)
            assertTrue(containsIcons)
            assertTrue(containsText)
            this.containsControls = containsControls
            return recommendedTimeoutMillis
        }
    }
}
