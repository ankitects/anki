/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
