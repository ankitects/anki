// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import android.appwidget.AppWidgetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.Consts
import com.ichi2.widget.cardanalysis.CardAnalysisWidget
import com.ichi2.widget.cardanalysis.CardAnalysisWidgetPreferences
import com.ichi2.widget.deckpicker.DeckPickerWidget
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckWidgetUpdateTest : RobolectricTest() {
    private val appWidgetManager: AppWidgetManager
        get() = AppWidgetManager.getInstance(targetContext)

    @Test
    fun `deck picker widget does not crash when the collection cannot be opened`() {
        enableNullCollection()

        DeckPickerWidget.updateWidget(targetContext, appWidgetManager, WIDGET_ID, longArrayOf(Consts.DEFAULT_DECK_ID))
        advanceRobolectricLooper()
    }

    @Test
    fun `card analysis widget does not crash when the collection cannot be opened`() {
        CardAnalysisWidgetPreferences(targetContext).saveSelectedDeck(WIDGET_ID, Consts.DEFAULT_DECK_ID)
        enableNullCollection()

        CardAnalysisWidget.updateWidget(targetContext, appWidgetManager, WIDGET_ID)
        advanceRobolectricLooper()
    }

    companion object {
        private val WIDGET_ID = AppWidgetId(1)
    }
}
