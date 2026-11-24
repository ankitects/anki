// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.widget.cardanalysis

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View.MeasureSpec
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.libanki.CardType
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.QueueType
import com.ichi2.widget.AppWidgetId
import com.ichi2.widget.cardanalysis.CardAnalysisWidget
import com.ichi2.widget.cardanalysis.CardAnalysisWidgetPreferences
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * `./gradlew :AnkiDroid:recordRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.widget.cardanalysis.CardAnalysisWidgetScreenshotTest"`
 *
 * Use the suite's default SDK until the backend supports multiple Robolectric classloaders.
 */
class CardAnalysisWidgetScreenshotTest : ScreenshotTest() {
    @Test
    fun size3x2() =
        runTest {
            captureDeck("size_3x2", widthDp = 262, heightDp = 220)
        }

    @Test
    fun size4x2() =
        runTest {
            captureDeck("size_4x2", widthDp = 349, heightDp = 220)
        }

    @Test
    fun size2x2() =
        runTest {
            captureDeck("size_2x2", widthDp = 175, heightDp = 220)
        }

    @Test
    fun size2x2TwoWords() =
        runTest {
            captureDeck("size_2x2_two_words", widthDp = 175, heightDp = 220, deckName = "Spanish Verbs")
        }

    @Test
    fun size2x1() =
        runTest {
            captureDeck("size_2x1", widthDp = 175, heightDp = 102)
        }

    @Test
    fun minimumSize() =
        runTest {
            captureDeck("minimum_size", widthDp = 84, heightDp = 56)
        }

    @Test
    fun size3x2LargeFont() =
        runTest {
            captureDeck("size_3x2_large_font", widthDp = 262, heightDp = 220, fontScale = 2f)
        }

    @Test
    fun size2x1LargeFont() =
        runTest {
            captureDeck("size_2x1_large_font", widthDp = 175, heightDp = 102, fontScale = 2f)
        }

    @Test
    fun missingDeck() =
        runTest {
            captureWidget("missing_deck", deckId = null, widthDp = 262, heightDp = 220)
        }

    @Test
    fun missingDeckMinimumSize() =
        runTest {
            captureWidget("missing_deck_minimum_size", deckId = null, widthDp = 84, heightDp = 56)
        }

    @Test
    fun emptyCollection() =
        runTest {
            captureWidget("empty_collection", deckId = Consts.DEFAULT_DECK_ID, widthDp = 262, heightDp = 220)
        }

    @Test
    fun emptyCollectionMinimumSize() =
        runTest {
            captureWidget("empty_collection_minimum_size", deckId = Consts.DEFAULT_DECK_ID, widthDp = 84, heightDp = 56)
        }

    private fun TestScope.captureDeck(
        name: String,
        widthDp: Int,
        heightDp: Int,
        fontScale: Float = 1f,
        deckName: String = "Pharmacology",
    ) {
        val deckId =
            addDeck(deckName)
                .withNotes(count = 12)
                .withNotes(count = 3, queueType = QueueType.Lrn)
        addNotes(25).forEach { note ->
            note.firstCard().update {
                did = deckId
                type = CardType.Rev
                queue = QueueType.Rev
                due = 0
            }
        }
        captureWidget(name, deckId, widthDp, heightDp, fontScale)
    }

    private fun TestScope.captureWidget(
        name: String,
        deckId: DeckId?,
        widthDp: Int,
        heightDp: Int,
        fontScale: Float = 1f,
    ) {
        RuntimeEnvironment.setFontScale(fontScale)
        CardAnalysisWidgetPreferences(targetContext).saveSelectedDeck(WIDGET_ID, deckId)
        val manager = AppWidgetManager.getInstance(targetContext)
        shadowOf(manager).bindAppWidgetId(WIDGET_ID.id, ComponentName(targetContext, CardAnalysisWidget::class.java))
        manager.updateAppWidgetOptions(
            WIDGET_ID.id,
            Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val size = SizeF(widthDp.toFloat(), heightDp.toFloat())
                    putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(size))
                }
            },
        )
        CardAnalysisWidget.updateWidget(targetContext, manager, WIDGET_ID)
        advanceUntilIdle()
        val view = requireNotNull(shadowOf(manager).getViewFor(WIDGET_ID.id))
        val width = widthDp.dp.toPx(targetContext)
        val height = heightDp.dp.toPx(targetContext)
        view.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        captureView(name, view)
    }

    companion object {
        private val WIDGET_ID = AppWidgetId(1)
    }
}
