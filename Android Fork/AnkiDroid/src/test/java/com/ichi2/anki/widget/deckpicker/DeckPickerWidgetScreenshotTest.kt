// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.widget.deckpicker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View.MeasureSpec
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.widget.AppWidgetId
import com.ichi2.widget.deckpicker.DeckPickerWidget
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * `./gradlew :AnkiDroid:recordRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.widget.deckpicker.DeckPickerWidgetScreenshotTest"`
 *
 * Use the suite's default SDK until the backend supports multiple Robolectric classloaders.
 */
class DeckPickerWidgetScreenshotTest : ScreenshotTest() {
    @Test
    fun compact() =
        runTest {
            captureWidget("compact", heightDp = 102)
        }

    @Test
    fun fullHeight() =
        runTest {
            captureWidget("full_height", heightDp = 220)
        }

    @Test
    fun compactLargeFont() =
        runTest {
            captureWidget("compact_large_font", heightDp = 102, fontScale = 2f)
        }

    @Test
    fun fullHeightLargeFont() =
        runTest {
            captureWidget("full_height_large_font", heightDp = 220, fontScale = 2f)
        }

    @Test
    fun minimumHeight() =
        runTest {
            captureWidget("minimum_height", heightDp = 50)
        }

    @Test
    fun minimumHeightLargeFont() =
        runTest {
            captureWidget("minimum_height_large_font", heightDp = 50, fontScale = 2f)
        }

    private fun TestScope.captureWidget(
        name: String,
        heightDp: Int,
        fontScale: Float = 1f,
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        val deckIds =
            listOf("✅ French", "✅ German", "✅ Spanish", "✅ Italian", "✅ Japanese")
                .mapIndexed { index, name -> addDeck(name).also { addNoteToDeck(it, count = index + 1) } }
                .toLongArray()
        val manager = AppWidgetManager.getInstance(targetContext)
        val widgetId = AppWidgetId(1)
        shadowOf(manager).bindAppWidgetId(widgetId.id, ComponentName(targetContext, DeckPickerWidget::class.java))
        manager.updateAppWidgetOptions(
            widgetId.id,
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 349)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 349)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(349f, heightDp.toFloat())))
                }
            },
        )
        DeckPickerWidget.updateWidget(targetContext, manager, widgetId, deckIds)
        advanceUntilIdle()
        val view = requireNotNull(shadowOf(manager).getViewFor(widgetId.id))
        val width = 349.dp.toPx(targetContext)
        val height = heightDp.dp.toPx(targetContext)
        view.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        captureView(name, view)
    }
}
