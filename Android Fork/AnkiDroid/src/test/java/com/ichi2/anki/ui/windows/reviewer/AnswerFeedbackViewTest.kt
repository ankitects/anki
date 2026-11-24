// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.ui.windows.reviewer

import android.app.Activity
import android.os.Looper
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer.Rating
import com.ichi2.testutils.setSystemAnimationsEnabled
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@RunWith(AndroidJUnit4::class)
class AnswerFeedbackViewTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val view = AnswerFeedbackView(activity).also { activity.setContentView(it) }

    @Test
    fun `feedback fades in, holds, then hides`() {
        setSystemAnimationsEnabled(true)
        view.show(Rating.GOOD)
        assertEquals(View.VISIBLE, view.visibility)

        advanceBy(500.milliseconds)
        assertEquals(1f, view.alpha)

        advanceBy(500.milliseconds)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `feedback is opaque when animations are disabled after an animated hide`() {
        setSystemAnimationsEnabled(true)
        view.show(Rating.GOOD)
        advanceBy(1000.milliseconds)

        setSystemAnimationsEnabled(false)
        view.show(Rating.GOOD)

        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1f, view.alpha)
        advanceBy(800.milliseconds)
        assertEquals(View.GONE, view.visibility)
    }

    @Test
    fun `showing during fade out cancels the pending hide`() {
        setSystemAnimationsEnabled(true)
        view.show(Rating.GOOD)
        advanceBy(600.milliseconds)

        view.show(Rating.GOOD)
        advanceBy(500.milliseconds)

        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1f, view.alpha)
    }

    private fun advanceBy(duration: Duration) = shadowOf(Looper.getMainLooper()).idleFor(duration.toJavaDuration())
}
