/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import android.net.Uri
import android.view.ViewConfiguration
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.TapGestureMode
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.ext.clamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs

/**
 * Parses gestures like taps and swipes based on coordinate data passed within an [Uri].
 *
 * @see parse
 */
class GestureParser(
    private val scope: CoroutineScope,
    private val isDoubleTapEnabled: Boolean,
    private val gestureMode: TapGestureMode = Prefs.tapGestureMode,
    swipeSensitivity: Float = Prefs.swipeSensitivity,
) {
    private val swipeThresholdBase = 100 / swipeSensitivity
    private var lastTapTime = 0L
    private var singleTapJob: Job? = null
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()

    @VisibleForTesting
    fun parseInternal(
        uri: Uri,
        webViewState: WebViewState,
        block: (Gesture?) -> Unit,
    ) {
        val gesture =
            if (uri.host == MULTI_FINGER_HOST) {
                getMultiTouchGesture(uri)
            } else {
                val data = GestureData.fromUri(uri) ?: return
                val swipeThreshold = swipeThresholdBase / webViewState.scale
                if (abs(data.deltaX) > swipeThreshold || abs(data.deltaY) > swipeThreshold) {
                    determineSwipeGesture(data)
                } else {
                    handleTap(data, webViewState, block)
                    return
                }
            }
        block(gesture)
    }

    /**
     * Analyzes the given [Uri] and returns the corresponding [Gesture].
     *
     * @param uri The [Uri] containing gesture data.
     * @param scale The current scale of the WebView.
     * @param webView The source WebView, used to access its current scroll and size properties.
     * @return The parsed [Gesture], or `null` if the gesture is invalid or should be ignored.
     */
    fun parse(
        uri: Uri,
        scale: Float,
        webView: WebView,
        block: (Gesture?) -> Unit,
    ) {
        val webViewState =
            WebViewState(
                scale = scale,
                scrollX = webView.scrollX,
                scrollY = webView.scrollY,
                width = webView.measuredWidth,
                height = webView.measuredHeight,
            )
        parseInternal(uri, webViewState, block)
    }

    private fun handleTap(
        data: GestureData,
        webViewState: WebViewState,
        block: (Gesture?) -> Unit,
    ) {
        val isPotentialDoubleTap = data.time - lastTapTime < doubleTapTimeout
        lastTapTime = data.time
        if (isDoubleTapEnabled) {
            if (isPotentialDoubleTap) {
                // Confirmed double tap. Cancel any pending single tap and fire double tap.
                singleTapJob?.cancel()
                singleTapJob = null
                lastTapTime = 0 // avoid retriggering double tap if a new quick tap comes
                block(Gesture.DOUBLE_TAP)
            } else {
                // Potential single tap. Schedule it to run after a delay.
                singleTapJob =
                    scope.launch {
                        delay(doubleTapTimeout)
                        block(getTap(data, webViewState))
                    }
            }
        } else {
            if (!isPotentialDoubleTap) {
                block(getTap(data, webViewState))
            } // Otherwise, ignore the double tap.
        }
    }

    /**
     * Determines the swipe gesture based on deltas and scroll direction.
     *
     * [GestureData.scrollDirection]: Indicates whether the underlying web content at the gesture's origin
     * is scrollable. This value is determined by the `getScrollDirection`
     * function in `ankidroid-reviewer.js` and is used to prevent custom swipe gestures
     * from overriding the browser's native scrolling behavior. It can contain:
     * - 'h': The content is horizontally scrollable.
     * - 'v': The content is vertically scrollable.
     * - "hv": The content is scrollable in both directions.
     * - `null`: The content is not scrollable.
     * @return The swipe [Gesture], or `null` if the swipe is in a direction that is scrollable
     * by the underlying web content.
     */
    private fun determineSwipeGesture(data: GestureData): Gesture? =
        if (abs(data.deltaX) > abs(data.deltaY)) { // Horizontal swipe
            when {
                data.scrollDirection?.contains('h') == true -> null
                data.deltaX > 0 -> Gesture.SWIPE_RIGHT
                else -> Gesture.SWIPE_LEFT
            }
        } else { // Vertical swipe
            when {
                data.scrollDirection?.contains('v') == true -> null
                data.deltaY > 0 -> Gesture.SWIPE_DOWN
                else -> Gesture.SWIPE_UP
            }
        }

    /** Determines the tap gesture based on the configured [TapGestureMode]. */
    private fun getTap(
        data: GestureData,
        state: WebViewState,
    ): Gesture =
        when (gestureMode) {
            TapGestureMode.FOUR_POINT -> getFourPointsTap(data, state)
            TapGestureMode.NINE_POINT -> getNinePointsTap(data, state)
        }

    /**
     * Determines the tap area by dividing the view into four triangular regions
     * using its main and anti-diagonals.
     * @return The [Gesture] corresponding to the tapped area (TAP_TOP, TAP_BOTTOM, TAP_LEFT, or TAP_RIGHT).
     */
    private fun getFourPointsTap(
        data: GestureData,
        state: WebViewState,
    ): Gesture {
        val adjustedX = state.getAdjustedTapPosition(data.x, state.scrollX)
        val adjustedY = state.getAdjustedTapPosition(data.y, state.scrollY)
        val normalizedX = adjustedX / state.width
        val normalizedY = adjustedY / state.height

        /*
         * The "main" diagonal runs from top-left (0,0) to bottom-right (width,height).
         * The "anti" diagonal runs from top-right (width,0) to bottom-left (0,height).
         * (0,0)      (width,0)
         * +-----------+
         * | \   T   / |
         * |  \     /  |
         * |   \   /   |
         * |    \ /    |
         * |L    X    R|
         * |    / \    |
         * |   /   \   |
         * |  /     \  |
         * | /   B   \ |
         * +-----------+
         * (0,height) (width,height)
         */
        val isRightOfMainDiagonal = normalizedX > normalizedY
        val isBelowAntiDiagonal = normalizedX + normalizedY > 1

        return when {
            isRightOfMainDiagonal && isBelowAntiDiagonal -> Gesture.TAP_RIGHT
            isRightOfMainDiagonal && !isBelowAntiDiagonal -> Gesture.TAP_TOP
            !isRightOfMainDiagonal && isBelowAntiDiagonal -> Gesture.TAP_BOTTOM
            else -> Gesture.TAP_LEFT // !isRightOfMainDiagonal && !isBelowAntiDiagonal
        }
    }

    private fun getNinePointsTap(
        data: GestureData,
        state: WebViewState,
    ): Gesture {
        val row = state.getGridRow(data)
        val column = state.getGridColumn(data)
        return gestureGrid[row][column]
    }

    private fun getMultiTouchGesture(uri: Uri): Gesture? {
        val touchCount = uri.getIntQuery(PARAM_TOUCH_COUNT) ?: return null
        return when (touchCount) {
            2 -> Gesture.TWO_FINGER_TAP
            3 -> Gesture.THREE_FINGER_TAP
            4 -> Gesture.FOUR_FINGER_TAP
            else -> {
                Timber.w("Invalid multi-finger tap count %d", touchCount)
                null
            }
        }
    }

    /** Raw gesture data extracted from the URI. */
    data class GestureData(
        val x: Int,
        val y: Int,
        val deltaX: Int,
        val deltaY: Int,
        val time: Long,
        val scrollDirection: String?,
    ) {
        companion object {
            fun fromUri(uri: Uri): GestureData? =
                run {
                    GestureData(
                        x = uri.getIntQuery(PARAM_X) ?: return@run null,
                        y = uri.getIntQuery(PARAM_Y) ?: return@run null,
                        deltaX = uri.getIntQuery(PARAM_DELTA_X) ?: return@run null,
                        deltaY = uri.getIntQuery(PARAM_DELTA_Y) ?: return@run null,
                        time = uri.getQueryParameter(PARAM_TIME)?.toLongOrNull() ?: return@run null,
                        scrollDirection = uri.getQueryParameter(PARAM_SCROLL_DIRECTION),
                    )
                }
        }
    }

    /** Encapsulates the state of the WebView relevant for gesture parsing. */
    data class WebViewState(
        val scale: Float,
        val scrollX: Int,
        val scrollY: Int,
        val width: Int,
        val height: Int,
    ) {
        fun getAdjustedTapPosition(
            tapPosition: Int,
            scrolledDistance: Int,
        ): Float = (tapPosition * scale) - scrolledDistance

        fun getGridRow(data: GestureData) = getGridIndex(data.y, scrollY, height)

        fun getGridColumn(data: GestureData) = getGridIndex(data.x, scrollX, width)

        /**
         * Calculates the grid index (row or column) for a tap coordinate.
         *
         * This function translates a raw screen tap coordinate into an index (0, 1, or 2) for one
         * dimension of the 3x3 gesture grid. It accounts for the WebView's current scroll position
         * and zoom level.
         *
         * @param tapPosition The raw client coordinate (X or Y) of the tap.
         * @param scrolledDistance The distance the WebView is scrolled along that axis.
         * @param dimensionSize The measured size (width or height) of the WebView.
         * @return The calculated grid index, constrained to be between 0 and 2.
         */
        private fun getGridIndex(
            tapPosition: Int,
            scrolledDistance: Int,
            dimensionSize: Int,
        ): Int {
            if (dimensionSize == 0) return 0 // Avoid division by zero
            val adjustedTap = getAdjustedTapPosition(tapPosition, scrolledDistance)
            val index = (adjustedTap / (dimensionSize / 3)).toInt()
            return index.clamp(0, 2)
        }
    }

    companion object {
        private fun Uri.getIntQuery(key: String) = getQueryParameter(key)?.toIntOrNull()

        private val gestureGrid =
            listOf(
                listOf(Gesture.TAP_TOP_LEFT, Gesture.TAP_TOP, Gesture.TAP_TOP_RIGHT),
                listOf(Gesture.TAP_LEFT, Gesture.TAP_CENTER, Gesture.TAP_RIGHT),
                listOf(Gesture.TAP_BOTTOM_LEFT, Gesture.TAP_BOTTOM, Gesture.TAP_BOTTOM_RIGHT),
            )

        @VisibleForTesting
        const val PARAM_X = "x"

        @VisibleForTesting
        const val PARAM_Y = "y"

        @VisibleForTesting
        const val PARAM_DELTA_X = "deltaX"

        @VisibleForTesting
        const val PARAM_DELTA_Y = "deltaY"

        @VisibleForTesting
        const val PARAM_TIME = "time"

        @VisibleForTesting
        const val PARAM_SCROLL_DIRECTION = "scrollDirection"

        @VisibleForTesting
        const val PARAM_TOUCH_COUNT = "touchCount"

        @VisibleForTesting
        const val MULTI_FINGER_HOST = "multiFingerTap"
    }
}
