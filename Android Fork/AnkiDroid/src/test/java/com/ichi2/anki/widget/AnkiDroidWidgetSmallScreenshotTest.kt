// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.widget

import android.os.Build
import android.view.View.MeasureSpec
import androidx.core.view.drawToBitmap
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.utils.dp
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Screenshots of the layout shown before the small widget's first update.
 *
 * `./gradlew :AnkiDroid:recordRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.widget.AnkiDroidWidgetSmallScreenshotTest"`
 */
@Config(sdk = [Build.VERSION_CODES.R, Build.VERSION_CODES.S])
class AnkiDroidWidgetSmallScreenshotTest : ScreenshotTest() {
    @Test
    @OptIn(ExperimentalRoborazziApi::class)
    fun initial() {
        val classDir = "build/outputs/roborazzi/${javaClass.simpleName}"
        val options = provideRoborazziContext().options
        withSmallWidget {
            val size = 100.dp.toPx(root.context)
            val measureSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
            root.measure(measureSpec, measureSpec)
            root.layout(0, 0, size, size)
            root.drawToBitmap().captureRoboImage(
                filePath = "$classDir/${fileNamePrefix}initial_api${Build.VERSION.SDK_INT}.png",
                roborazziOptions = options.copy(compareOptions = options.compareOptions.copy(outputDirectoryPath = "$classDir/diffs")),
            )
        }
    }
}
