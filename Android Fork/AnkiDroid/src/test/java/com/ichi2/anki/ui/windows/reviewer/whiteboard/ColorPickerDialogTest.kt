// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.view.MotionEvent
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.themes.Themes
import com.skydoves.colorpickerview.ColorPickerView
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowDialog
import java.time.Duration
import kotlin.test.assertNotNull

/** Tests for [showColorPickerDialog] */
@RunWith(AndroidJUnit4::class)
class ColorPickerDialogTest {
    private lateinit var context: Context
    private var pickedColor: Int? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Themes.setTheme(context)
        // The dialog repositions its color bubble on every frame, so an unbounded
        // looper drain would never terminate. Pause the choreographer so frames only
        // run while the clock advances, and advance it in bounded steps (see idleMainLooper).
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(15))
    }

    @Test
    fun `picking a hue on a black-seeded picker keeps the hue visible`() {
        assertPickedHueIsVisible(seed = Color.BLACK)
    }

    @Test
    fun `picking a hue on a near-black-seeded picker keeps the hue visible`() {
        assertPickedHueIsVisible(seed = Color.rgb(3, 0, 0))
    }

    @Test
    fun `confirming without touching anything returns the initial color`() {
        val dialog = showPicker(initialColor = Color.BLACK)

        dialog.confirm()

        assertThat(pickedColor, equalTo(Color.BLACK))
    }

    @Test
    fun `picking a hue on a bright seed keeps its brightness`() {
        val seed = Color.rgb(128, 0, 0)
        val dialog = showPicker(initialColor = seed)

        dialog.wheel().tapRightEdge()
        idleMainLooper()
        dialog.confirm()

        val color = assertNotNull(pickedColor, "a color should be returned on OK")
        val pickedBrightness = FloatArray(3).also { Color.colorToHSV(color, it) }[2]
        val seedBrightness = FloatArray(3).also { Color.colorToHSV(seed, it) }[2]
        assertThat(
            "brightness must be left untouched for a non-near-black seed",
            pickedBrightness.toDouble(),
            closeTo(seedBrightness.toDouble(), 0.02),
        )
    }

    /** Seeds the picker with [seed], taps a bright hue, and asserts the result is not swallowed to black. */
    private fun assertPickedHueIsVisible(seed: Int) {
        val dialog = showPicker(initialColor = seed)

        dialog.wheel().tapRightEdge() // pure red at full saturation
        idleMainLooper()
        dialog.confirm()

        val color = assertNotNull(pickedColor, "a color should be returned on OK")
        val hsv = FloatArray(3).also { Color.colorToHSV(color, it) }
        assertThat("picked color is not black", color, not(equalTo(Color.BLACK)))
        assertThat("picked hue keeps full brightness", hsv[2], equalTo(1f))
    }

    /** Shows the color picker dialog and waits for the picker to finish initializing. */
    private fun showPicker(initialColor: Int): AlertDialog {
        context.showColorPickerDialog(initialColor) { pickedColor = it }
        val dialog =
            assertNotNull(
                ShadowDialog.getLatestDialog() as? AlertDialog,
                "color picker dialog should be shown",
            )
        repeat(3) { idleMainLooper() }
        return dialog
    }

    private fun AlertDialog.wheel(): ColorPickerView = assertNotNull(findViewById(com.skydoves.colorpickerview.R.id.colorPickerView))

    private fun AlertDialog.confirm() {
        getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        idleMainLooper()
    }

    private fun ColorPickerView.tapRightEdge() {
        assertThat("the wheel should be laid out", width, greaterThan(0))
        val x = width - 5f
        val y = height / 2f
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0, 10, MotionEvent.ACTION_UP, x, y, 0)
        dispatchTouchEvent(down)
        dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    /**
     * Bounded, because the dialog re-posts a frame callback forever: this runs the
     * pending messages plus a few frames, then stops instead of draining endlessly.
     */
    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
}
