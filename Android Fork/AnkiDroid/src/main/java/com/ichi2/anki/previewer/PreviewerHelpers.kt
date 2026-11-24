// SPDX-License-Identifier: GPL-3.0-or-later

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
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

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

    val colors = rootColorsCss(nightMode) { attr -> ThemeUtils.getThemeAttrColor(context, attr) }
    // Preserve the system font size while scaling text and em-based layout together.
    val fontScale = context.resources.configuration.fontScale
    val fontScaleCss = if (fontScale != 1f) "html { zoom: $fontScale }" else ""

    val jsAssets: List<String> =
        listOf(
            "backend/js/jquery.min.js",
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
                $fontScaleCss
            </style>
        </head>
        <body class="${bodyClass()}">
            <div id="qa" dir="auto"></div>
            $jsTxt
        </body>
        </html>
        """.trimIndent()
}

internal fun rootColorsCss(
    nightMode: Boolean,
    resolveColor: (attr: Int) -> Int,
): String {
    val rootNightMode = if (nightMode) "[class*=night-mode]" else ""
    val canvasColor = resolveColor(android.R.attr.colorBackground).toRGBHex()
    val fgColor = resolveColor(android.R.attr.textColor).toRGBHex()
    val themeColors =
        listOf(
            "primary" to AppCompatR.attr.colorPrimary,
            "on-primary" to MaterialR.attr.colorOnPrimary,
            "secondary" to MaterialR.attr.colorSecondary,
            "on-secondary" to MaterialR.attr.colorOnSecondary,
            "surface" to MaterialR.attr.colorSurface,
            "on-surface" to MaterialR.attr.colorOnSurface,
            "background" to android.R.attr.colorBackground,
            "on-background" to MaterialR.attr.colorOnBackground,
        ).joinToString(" ") { (name, attr) ->
            "--ankidroid-color-$name: ${resolveColor(attr).toRGBHex()};"
        }
    return ":root$rootNightMode { --canvas: $canvasColor; --fg: $fgColor; $themeColors }"
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
        strokeWidth = 0
    }
}
