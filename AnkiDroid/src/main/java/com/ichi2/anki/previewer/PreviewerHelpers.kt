/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.previewer

import android.content.Context
import android.view.ViewGroup.MarginLayoutParams
import androidx.appcompat.widget.ThemeUtils
import androidx.core.view.updateLayoutParams
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.ShapeAppearanceModel
import com.ichi2.anki.LanguageUtils
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.FrameStyle
import com.ichi2.themes.Themes
import com.ichi2.utils.toRGBHex
import org.intellij.lang.annotations.Language

/**
 * Not exactly equal to anki's stdHtml. Some differences:
 * * `ankidroid.css` and `ankidroid-cardviewer.js` are added
 *
 * Aimed to be used only for reviewing/previewing cards
 *
 * @param extraJsAssets paths of additional Javascript assets
 * in the `android_assets` folder to be included
 */
@Language("HTML")
fun stdHtml(
    context: Context = appContext,
    extraJsAssets: List<String> = emptyList(),
    nightMode: Boolean = false,
): String {
    val languageDirectionality = if (LanguageUtils.appLanguageIsRTL()) "rtl" else "ltr"
    val baseTheme = if (nightMode) "dark" else "light"
    val docClass = if (nightMode) "night-mode" else ""
    val rootNightMode = if (nightMode) "[class*=night-mode]" else ""

    val canvasColor = ThemeUtils.getThemeAttrColor(context, android.R.attr.colorBackground).toRGBHex()
    val fgColor = ThemeUtils.getThemeAttrColor(context, android.R.attr.textColor).toRGBHex()
    val colors = ":root$rootNightMode { --canvas: $canvasColor; --fg: $fgColor; }"

    val jsAssets: List<String> =
        listOf(
            "backend/js/jquery.min.js",
            "backend/js/mathjax.js",
            "backend/js/vendor/mathjax/tex-chtml-full.js",
            "backend/js/reviewer.js",
            "scripts/ankidroid-cardviewer.js",
        ) + extraJsAssets
    val jsTxt =
        jsAssets.joinToString("\n") {
            """<script src="file:///android_asset/$it"></script>"""
        }

    return """
        <!DOCTYPE html>
        <html class="$docClass" dir="$languageDirectionality" data-bs-theme="$baseTheme">
        <head>
            <title>AnkiDroid</title>
                <link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/root-vars.css">
                <link rel="stylesheet" type="text/css" href="file:///android_asset/backend/css/reviewer.css">
                <link rel="stylesheet" type="text/css" href="file:///android_asset/ankidroid.css">
            <style>
                .night-mode button { --canvas: #606060; --fg: #eee; }
                $colors
            </style>
        </head>
        <body class="${bodyClass()}">
            <div id="qa" dir="auto"></div>
            $jsTxt
        </body>
        </html>
        """.trimIndent()
}

/**
 * "mathjax-rendered" is a legacy class kept only to support old note types.
 *
 * @return body classes used when showing a card
 */
fun bodyClassForCardOrd(
    cardOrd: CardOrdinal,
    nightMode: Boolean = Themes.isNightTheme,
): String = "card card${cardOrd + 1} ${bodyClass(nightMode)} mathjax-rendered"

private fun bodyClass(nightMode: Boolean = Themes.isNightTheme): String = if (nightMode) "nightMode night_mode" else ""

fun MaterialCardView.setFrameStyle() {
    if (Prefs.frameStyle == FrameStyle.BOX && Prefs.isNewStudyScreenEnabled) {
        updateLayoutParams<MarginLayoutParams> {
            leftMargin = 0
            rightMargin = 0
        }
        cardElevation = 0F
        shapeAppearanceModel = ShapeAppearanceModel() // Remove corners
    }
}
