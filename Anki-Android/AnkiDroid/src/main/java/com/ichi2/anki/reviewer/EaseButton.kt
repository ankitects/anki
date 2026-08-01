/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.reviewer

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.common.utils.annotation.KotlinCleanup

/**
 * The UI of an ease button
 *
 * Currently contains a some business logic:
 * * [nextTime] is used by the API
 * * [canPerformClick] is used to determine if the answer is being shown and the button isn't blocked
 */
class EaseButton(
    private val rating: Rating,
    private val layout: LinearLayout,
    private val easeTextView: TextView,
    private val easeTimeView: TextView,
) {
    var height: Int
        get() = layout.layoutParams.height
        set(value) {
            layout.layoutParams.height = value
        }

    @get:JvmName("canPerformClick")
    val canPerformClick
        get() = layout.isEnabled && layout.isVisible

    var nextTime: String
        get() = easeTimeView.text.toString()
        set(value) {
            easeTimeView.text = value
        }

    fun hideNextReviewTime() {
        easeTimeView.visibility = View.GONE
    }

    fun setButtonScale(scale: Int) {
        val params = layout.layoutParams
        params.height = params.height * scale / 100
    }

    fun setVisibility(visibility: Int) {
        layout.visibility = visibility
    }

    fun setColor(color: Int) {
        layout.setBackgroundResource(color)
    }

    fun setListeners(easeHandler: AbstractFlashcardViewer.SelectEaseHandler) {
        layout.setOnClickListener { view: View -> easeHandler.onClick(view) }
        layout.setOnTouchListener { view: View, event: MotionEvent -> easeHandler.onTouch(view, event) }
    }

    fun detachFromParent() {
        if (layout.parent != null) {
            (layout.parent as ViewGroup).removeView(layout)
        }
    }

    fun addTo(toAddTo: LinearLayout) {
        toAddTo.addView(layout)
    }

    fun hide() {
        layout.visibility = View.GONE
        easeTimeView.text = ""
    }

    /** Perform a click if the button is visible */
    fun performSafeClick() {
        if (!canPerformClick) return
        layout.performClick()
    }

    /**
     * Makes the button clickable if it is the provided ease, otherwise enabled.
     *
     * @param currentEase The current ease of the card
     */
    @KotlinCleanup("Make the type non nullable.")
    fun unblockBasedOnEase(currentEase: Rating?) {
        if (this.rating == currentEase) {
            layout.isClickable = true
        } else {
            layout.isEnabled = true
        }
    }

    /**
     * Makes the button not clickable if it is the provided ease, otherwise disable it.
     *
     * @param currentEase The current ease of the card
     */
    fun blockBasedOnEase(currentEase: Rating) {
        if (this.rating == currentEase) {
            layout.isClickable = false
        } else {
            layout.isEnabled = false
        }
    }

    fun performClickWithVisualFeedback() {
        layout.requestFocus()
        layout.performClick()
    }

    fun requestFocus() {
    }

    fun setup(
        backgroundColor: Int,
        textColor: Int,
        @StringRes easeStringRes: Int,
    ) {
        layout.visibility = View.VISIBLE
        layout.setBackgroundResource(backgroundColor)
        easeTextView.setText(easeStringRes)
        easeTextView.setTextColor(textColor)
        easeTimeView.setTextColor(textColor)
    }
}
