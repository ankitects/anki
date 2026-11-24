// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.audiorecord

import android.view.ContextThemeWrapper
import android.view.View.MeasureSpec
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.themes.Themes
import org.junit.Test

/**
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot -Ptheme=all --tests "*AudioRecordViewScreenshotTest"`
 */
class AudioRecordViewScreenshotTest : ScreenshotTest() {
    @Test
    fun `idle recorder shows the disabled recording icon`() {
        Themes.updateCurrentTheme(targetContext)
        val context = ContextThemeWrapper(targetContext, Themes.currentTheme.styleResId)
        val view = AudioRecordView(context)
        view.setBackgroundColor(MaterialColors.getColor(context, android.R.attr.colorBackground, 0))

        val width = 360.dp.toPx(context)
        view.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, width, view.measuredHeight)

        captureView("idle", view)
    }
}
