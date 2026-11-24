// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.themes

import android.view.ContextThemeWrapper
import android.view.View.MeasureSpec
import android.widget.EditText
import android.widget.LinearLayout
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.utils.dp
import org.junit.Test

/**
 * The theme's hint color, on plain text fields. Screens mostly use TextInputLayout,
 * which colors its own label, so no screen shows it reliably.
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.themes.TextFieldHintScreenshotTest"`
 */
class TextFieldHintScreenshotTest : ScreenshotTest() {
    @Test
    fun `text field hints`() {
        Themes.updateCurrentTheme(targetContext)
        val context = ContextThemeWrapper(targetContext, Themes.currentTheme.styleResId)
        val padding = 16.dp.toPx(context)
        val fields =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padding, padding, padding, padding)
                setBackgroundColor(MaterialColors.getColor(context, android.R.attr.colorBackground, 0))
                addView(EditText(context).apply { hint = "Hint" })
                addView(
                    EditText(context).apply {
                        hint = "Disabled hint"
                        isEnabled = false
                    },
                )
            }
        fields.measure(
            MeasureSpec.makeMeasureSpec(360.dp.toPx(context), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        fields.layout(0, 0, fields.measuredWidth, fields.measuredHeight)
        captureView("text_field_hints", fields)
    }
}
