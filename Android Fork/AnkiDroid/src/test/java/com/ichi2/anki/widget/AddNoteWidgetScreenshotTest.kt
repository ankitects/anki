// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.view.View.MeasureSpec
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.widget.AddNoteWidget
import com.ichi2.widget.AppWidgetIds
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/**
 * `./gradlew :AnkiDroid:recordRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.widget.AddNoteWidgetScreenshotTest"`
 */
class AddNoteWidgetScreenshotTest : ScreenshotTest() {
    @Test
    fun defaultSize() {
        val manager = AppWidgetManager.getInstance(targetContext)
        shadowOf(manager).bindAppWidgetId(WIDGET_ID, ComponentName(targetContext, AddNoteWidget::class.java))

        AddNoteWidget.updateWidgets(targetContext, manager, AppWidgetIds(intArrayOf(WIDGET_ID)))

        val view = requireNotNull(shadowOf(manager).getViewFor(WIDGET_ID))
        val size = 100.dp.toPx(targetContext)
        val measureSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        view.measure(measureSpec, measureSpec)
        view.layout(0, 0, size, size)
        captureView("default_size", view)
    }

    companion object {
        private const val WIDGET_ID = 1
    }
}
