// SPDX-License-Identifier: GPL-3.0-or-later

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
    private val fontScale: Float,
) {
    /** Below could be in a better abstraction.  */
    fun appendCssStyle(style: StringBuilder) {
        // Preserve the system font size while scaling text and em-based layout together.
        if (fontScale != 1f) {
            style.append("html { zoom: $fontScale }\n")
        }

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
            fontScale: Float,
        ): CardAppearance {
            val cardZoom = preferences.getInt("cardZoom", 100)
            val imageZoom = preferences.getInt("imageZoom", 100)
            val centerVertically = preferences.getBoolean("centerVertically", false)
            return CardAppearance(customFonts, cardZoom, imageZoom, centerVertically, fontScale)
        }

        fun fixBoldStyle(content: String): String {
            // In order to display the bold style correctly, we have to change
            // font-weight to 700
            return content.replace("font-weight:600;", "font-weight:700;")
        }
    }
}
