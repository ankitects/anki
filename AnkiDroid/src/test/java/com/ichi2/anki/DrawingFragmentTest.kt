/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import android.graphics.Path
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.ui.windows.reviewer.whiteboard.WhiteboardFragment
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Tests for [DrawingFragment] */
@RunWith(AndroidJUnit4::class)
class DrawingFragmentTest : RobolectricTest() {
    @Test
    fun `back press finishes the activity when the whiteboard is empty`() {
        val (activity, _) = launchDrawingActivity()

        activity.onBackPressedDispatcher.onBackPressed()
        advanceRobolectricLooper()

        assertNull(ShadowDialog.getLatestDialog(), "no discard dialog should be shown for an empty whiteboard")
        assertThat("activity finishes immediately", activity.isFinishing, equalTo(true))
    }

    @Test
    fun `back press shows the discard dialog when the whiteboard has content`() {
        val (activity, whiteboard) = launchDrawingActivity()
        whiteboard.binding.whiteboardView.onNewPath
            ?.invoke(samplePath())
        advanceRobolectricLooper()

        activity.onBackPressedDispatcher.onBackPressed()
        advanceRobolectricLooper()

        val dialog =
            assertNotNull(
                ShadowDialog.getLatestDialog() as? AlertDialog,
                "discard dialog should be shown",
            )
        assertThat("activity is not finishing while dialog is open", activity.isFinishing, equalTo(false))

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()
        assertThat("activity finishes after discard is confirmed", activity.isFinishing, equalTo(true))
    }

    private fun launchDrawingActivity(): Pair<SingleFragmentActivity, WhiteboardFragment> {
        val activity = startRegularActivity<SingleFragmentActivity>(DrawingFragment.getIntent(targetContext))
        val drawingFragment =
            activity.supportFragmentManager.findFragmentByTag(SingleFragmentActivity.FRAGMENT_TAG) as DrawingFragment
        val whiteboardFragment =
            drawingFragment.childFragmentManager.findFragmentById(R.id.fragment_container) as WhiteboardFragment
        return activity to whiteboardFragment
    }

    private fun samplePath(): Path =
        Path().apply {
            moveTo(0f, 0f)
            lineTo(10f, 10f)
        }
}
