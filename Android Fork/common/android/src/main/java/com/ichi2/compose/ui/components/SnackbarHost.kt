// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.components

import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.material3.SnackbarHost as MaterialSnackbarHost
import com.google.android.material.snackbar.Snackbar as ViewSnackbar

/** A Material snackbar host using View duration presets and accessibility recommendations. */
@Composable
fun SnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    timeoutPaused: Boolean = false,
    snackbar: @Composable (SnackbarData) -> Unit = { AnkiDroidSnackbar(it, Modifier.padding(12.dp)) },
) {
    SnackbarHost(hostState, modifier, touch = null, timeoutPaused = timeoutPaused, snackbar = snackbar)
}

@Composable
internal fun SnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier,
    touch: State<SnackbarTouch?>?,
    timeoutPaused: Boolean = false,
    snackbar: @Composable (SnackbarData) -> Unit,
) {
    val accessibilityManager = LocalAccessibilityManager.current
    val duration = hostState.currentSnackbarData?.visuals?.duration
    val timeouts = remember(accessibilityManager, duration) { SnackbarAccessibilityManager(accessibilityManager, duration) }
    val data = hostState.currentSnackbarData
    val scheduledTouch = touch?.value
    LaunchedEffect(data, timeoutPaused, scheduledTouch, timeouts) {
        if (data != null && !timeoutPaused && scheduledTouch?.pressed != true) {
            delay(
                timeouts
                    .calculateRecommendedTimeoutMillis(
                        originalTimeoutMillis = 0L,
                        containsIcons = true,
                        containsText = true,
                        containsControls = data.visuals.actionLabel != null,
                    ).milliseconds,
            )
            // A pointer event may have changed the touch state before recomposition runs.
            if (touch?.value === scheduledTouch) data.dismiss()
        }
    }
    // Keep Material's transitions, but give our pausable timer sole ownership of dismissal.
    CompositionLocalProvider(LocalAccessibilityManager provides NoSnackbarTimeout) {
        MaterialSnackbarHost(hostState, modifier) { data ->
            // The timeout override applies only to the host, not to custom snackbar content.
            CompositionLocalProvider(LocalAccessibilityManager provides accessibilityManager) {
                snackbar(data)
            }
        }
    }
}

/** Each touch transition has its own identity, even if a tap starts and ends in one frame. */
internal class SnackbarTouch(
    val pressed: Boolean,
)

/** Disables Material's timer while the host above manages the current message's duration. */
private object NoSnackbarTimeout : AccessibilityManager {
    override fun calculateRecommendedTimeoutMillis(
        originalTimeoutMillis: Long,
        containsIcons: Boolean,
        containsText: Boolean,
        containsControls: Boolean,
    ): Long = Long.MAX_VALUE
}

/**
 * Uses XML View snackbar duration in Compose.
 *
 * Fixes an Android bug where `LENGTH_SHORT` is mapped to `LENGTH_LONG`.
 */
private class SnackbarAccessibilityManager(
    private val delegate: AccessibilityManager?,
    private val duration: SnackbarDuration?,
) : AccessibilityManager {
    override fun calculateRecommendedTimeoutMillis(
        originalTimeoutMillis: Long,
        containsIcons: Boolean,
        containsText: Boolean,
        containsControls: Boolean,
    ): Long {
        // Match View snackbars: API 24–28 use milliseconds; API 29+ pass presets to accessibility.
        // The wrapper below preserves Short when Android returns the Long preset (0).
        val useDurationConstants = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && delegate != null
        val timeout =
            when (duration) {
                SnackbarDuration.Short -> if (useDurationConstants) ViewSnackbar.LENGTH_SHORT.toLong() else 1_500L
                SnackbarDuration.Long -> if (useDurationConstants) ViewSnackbar.LENGTH_LONG.toLong() else 2_750L
                SnackbarDuration.Indefinite -> return Long.MAX_VALUE
                null -> originalTimeoutMillis
            }
        val recommended =
            delegate?.calculateRecommendedSnackbarTimeoutMillis(timeout, containsIcons, containsText, containsControls) ?: timeout
        return when (recommended) {
            ViewSnackbar.LENGTH_SHORT.toLong() -> 1_500L
            ViewSnackbar.LENGTH_LONG.toLong() -> 2_750L
            else -> recommended
        }
    }
}

/**
 * Fixes https://issuetracker.google.com/issues/585443264 - `LENGTH_SHORT` was set to `LENGTH_LONG`
 */
private fun AccessibilityManager.calculateRecommendedSnackbarTimeoutMillis(
    originalTimeoutMillis: Long,
    containsIcons: Boolean,
    containsText: Boolean,
    containsControls: Boolean,
): Long {
    val recommended = calculateRecommendedTimeoutMillis(originalTimeoutMillis, containsIcons, containsText, containsControls)
    // Unlike AnkiDroid's View snackbars, preserve Short when Android's max(-1, 0)
    // turns it into Long. Positive accessibility recommendations pass through unchanged.
    return if (originalTimeoutMillis == ViewSnackbar.LENGTH_SHORT.toLong() && recommended == 0L) originalTimeoutMillis else recommended
}
