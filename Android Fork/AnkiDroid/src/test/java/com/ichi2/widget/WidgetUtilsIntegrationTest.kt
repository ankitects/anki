// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import android.appwidget.AppWidgetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.RobolectricTest
import com.ichi2.widget.cardanalysis.CardAnalysisWidget
import com.ichi2.widget.deckpicker.DeckPickerWidget
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/** @see getAppWidgetManager */
@RunWith(AndroidJUnit4::class)
class WidgetUtilsIntegrationTest : RobolectricTest() {
    // on a Supernote A5X, AppWidgetManager.getInstance(context) returns null
    @Test
    fun `app runs with no widget manager`() {
        super.startRegularActivity<DeckPicker>() // WARN: This didn't crash before fixes applied
    }

    @Test
    fun `deck picker widgets update with no widget manager`() {
        DeckPickerWidget.updateDeckPickerWidgets(targetContext)
    }

    @Test
    fun `card analysis widgets update with no widget manager`() {
        CardAnalysisWidget.updateCardAnalysisWidgets(targetContext)
    }

    @Test
    fun `deck picker widget ignores update broadcasts with no widget manager`() {
        DeckPickerWidget().onReceive(targetContext, widgetUpdateIntent())
    }

    @Test
    fun `card analysis widget ignores update broadcasts with no widget manager`() {
        CardAnalysisWidget().onReceive(targetContext, widgetUpdateIntent())
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            mockkStatic(AppWidgetManager::class)
            every { AppWidgetManager.getInstance(any()) } answers { null }
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            unmockkStatic(AppWidgetManager::class)
        }
    }
}
