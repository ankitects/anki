// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.view.View
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import com.ichi2.anki.Flag
import com.ichi2.anki.R

/** Handles the star and flag marker for the card viewer  */
class CardMarker(
    private val markView: ImageView,
    private val flagView: ImageView,
) {
    /** Sets the mark icon on a card (the star)  */
    fun displayMark(markStatus: Boolean) {
        if (markStatus) {
            markView.visibility = View.VISIBLE
            markView.setImageResource(R.drawable.ic_star_white_bordered_24dp)
        } else {
            markView.visibility = View.INVISIBLE
        }
    }

    /** Whether the mark icon is visible on the toolbar */
    val isDisplayingMark: Boolean
        get() = markView.isVisible

    /** Sets the flag icon on the card  */
    fun displayFlag(flag: Flag) {
        when (flag) {
            Flag.RED, Flag.BLUE, Flag.GREEN, Flag.ORANGE, Flag.PINK, Flag.PURPLE, Flag.TURQUOISE -> {
                setFlagView(flag.drawableRes)
            }
            Flag.NONE -> flagView.visibility = View.INVISIBLE
        }
    }

    private fun setFlagView(
        @DrawableRes drawableId: Int,
    ) {
        // set the resource before to ensure we display the correct icon.
        flagView.setImageResource(drawableId)
        flagView.visibility = View.VISIBLE
    }
}
