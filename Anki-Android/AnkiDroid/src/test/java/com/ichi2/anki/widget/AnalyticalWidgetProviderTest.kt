/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *  Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.appwidget.AppWidgetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.analytics.UsageAnalytics
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

@RunWith(AndroidJUnit4::class)
class AnalyticalWidgetProviderTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        mockkObject(UsageAnalytics)
        every { UsageAnalytics.sendAnalyticsEvent(any(), any()) } answers { }
    }

    @After
    override fun tearDown() {
        super.tearDown()
        unmockkObject(UsageAnalytics)
    }

    @Test
    fun testAnalyticsEventLogging() {
        val widgetProvider = TestWidgetProvider()

        widgetProvider.onEnabled(targetContext)

        verify { UsageAnalytics.sendAnalyticsEvent("TestWidgetProvider", "enabled") }
    }

    private class TestWidgetProvider : AnalyticsWidgetProvider() {
        override fun performUpdate(
            context: android.content.Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: AppWidgetIds,
            usageAnalytics: UsageAnalytics,
        ) {
            // Do nothing
        }
    }
}
