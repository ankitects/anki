// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.analytics.AnalyticsEvent
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.targetContext
import com.ichi2.widget.AnalyticsWidgetProvider
import com.ichi2.widget.AppWidgetIds
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class AnalyticalWidgetProviderTest : AndroidTest {
    @Before
    fun setUp() {
        mockkObject(Analytics)
        every { Analytics.send(any()) } answers { }
    }

    @After
    fun tearDown() {
        unmockkObject(Analytics)
    }

    @Test
    fun testAnalyticsEventLogging() {
        val widgetProvider = TestWidgetProvider()

        widgetProvider.onEnabled(targetContext)

        verify {
            Analytics.send(AnalyticsEvent.WidgetEnabled("TestWidgetProvider"))
        }
    }

    @Test
    fun `update is skipped until storage is decided`() {
        val widgetProvider = TestWidgetProvider()

        widgetProvider.receiveUpdate()

        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(widgetProvider.updated, "widget updated before storage was decided")
    }

    private fun TestWidgetProvider.receiveUpdate() =
        onReceive(
            targetContext,
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(1)),
        )

    private class TestWidgetProvider : AnalyticsWidgetProvider() {
        @Volatile
        var updated = false

        override fun performUpdate(
            context: android.content.Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: AppWidgetIds,
        ) {
            updated = true
        }
    }
}
