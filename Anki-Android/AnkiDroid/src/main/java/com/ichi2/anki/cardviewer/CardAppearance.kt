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
package com.ichi2.anki.cardviewer

import android.content.SharedPreferences
import androidx.annotation.CheckResult
import com.ichi2.anki.reviewer.ReviewerCustomFonts
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.themes.Themes.currentTheme

/** Responsible for calculating CSS and element styles and modifying content on a flashcard  */
class CardAppearance(
    private val customFonts: ReviewerCustomFonts,
    private val cardZoom: Int,
    private val imageZoom: Int,
    private val centerVertically: Boolean,
) {
    /** Below could be in a better abstraction.  */
    fun appendCssStyle(style: StringBuilder) {
        // Zoom cards
        if (cardZoom != 100) {
            style.append("body { zoom: ${cardZoom / 100.0} }\n")
        }

        // Zoom images
        if (imageZoom != 100) {
            style.append("img { zoom: ${imageZoom / 100.0} }\n")
        }
    }

    @CheckResult
    fun getCssClasses(): String {
        val cardClass = StringBuilder()
        if (centerVertically) {
            cardClass.append(" vertically_centered")
        }
        if (currentTheme is NightTheme) {
            // Enable the night-mode class
            cardClass.append(" night_mode nightMode")

            // Emit the dark_mode selector to allow dark theme overrides
            if (currentTheme == NightTheme.DARK) {
                cardClass.append(" ankidroid_dark_mode")
            }
        } else {
            // Emit the plain_mode selector to allow plain theme overrides
            if (currentTheme == DayTheme.PLAIN) {
                cardClass.append(" ankidroid_plain_mode")
            }
        }
        return cardClass.toString()
    }

    val style: String
        get() {
            val style = StringBuilder()
            customFonts.updateCssStyle(style)
            appendCssStyle(style)
            return style.toString()
        }

    fun getCardClass(oneBasedCardOrdinal: Int): String = "card card$oneBasedCardOrdinal" + getCssClasses()

    companion object {
        private val nightModeClassRegex = Regex("\\.night(?:_m|M)ode\\b")

        fun create(
            customFonts: ReviewerCustomFonts,
            preferences: SharedPreferences,
        ): CardAppearance {
            val cardZoom = preferences.getInt("cardZoom", 100)
            val imageZoom = preferences.getInt("imageZoom", 100)
            val centerVertically = preferences.getBoolean("centerVertically", false)
            return CardAppearance(customFonts, cardZoom, imageZoom, centerVertically)
        }

        fun fixBoldStyle(content: String): String {
            // In order to display the bold style correctly, we have to change
            // font-weight to 700
            return content.replace("font-weight:600;", "font-weight:700;")
        }
    }
}
