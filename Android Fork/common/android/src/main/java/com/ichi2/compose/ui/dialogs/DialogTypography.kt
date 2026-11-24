// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import android.text.Layout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import com.google.android.material.textview.MaterialTextView
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

/** Read the same text appearances as the View dialog, including its font metrics. */
@Composable
internal fun dialogTypography(): Typography {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    return remember(context, configuration, density, typography) {
        val themedContext = AlertDialog.Builder(context).context
        // XML text styles overlay the normal TextView widget defaults.
        val paragraphDefaults = MaterialTextView(themedContext)
        typography.copy(
            headlineSmall =
                MaterialTextView(
                    themedContext,
                    null,
                    MaterialR.attr.materialAlertDialogTitleTextStyle,
                ).dialogTextStyle(density, paragraphDefaults),
            bodyMedium =
                MaterialTextView(
                    themedContext,
                    null,
                    MaterialR.attr.materialAlertDialogBodyTextStyle,
                ).dialogTextStyle(density, paragraphDefaults),
            labelLarge = AppCompatButton(themedContext, null, AppCompatR.attr.buttonBarPositiveButtonStyle).dialogTextStyle(density),
        )
    }
}

private fun TextView.dialogTextStyle(
    density: Density,
    paragraphDefaults: TextView = this,
): TextStyle =
    with(density) {
        TextStyle(
            fontFamily = FontFamily(typeface),
            fontSize = textSize.toSp(),
            letterSpacing = letterSpacing.em,
            // Compose scales sp line heights proportionally at large font sizes. The
            // View has already resolved its line height, so preserve that pixel ratio.
            lineHeight = (lineHeight / textSize).em,
            platformStyle = PlatformTextStyle(includeFontPadding = includeFontPadding),
            lineHeightStyle = null,
            lineBreak =
                LineBreak(
                    strategy =
                        when (paragraphDefaults.breakStrategy) {
                            Layout.BREAK_STRATEGY_HIGH_QUALITY -> LineBreak.Strategy.HighQuality
                            Layout.BREAK_STRATEGY_BALANCED -> LineBreak.Strategy.Balanced
                            else -> LineBreak.Strategy.Simple
                        },
                    strictness = LineBreak.Strictness.Default,
                    wordBreak = LineBreak.WordBreak.Default,
                ),
            hyphens = if (paragraphDefaults.hyphenationFrequency == Layout.HYPHENATION_FREQUENCY_NONE) Hyphens.None else Hyphens.Auto,
        )
    }
