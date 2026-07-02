/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki

import android.content.Context
import android.content.res.Resources
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.brsanthu.googleanalytics.GoogleAnalytics
import com.brsanthu.googleanalytics.GoogleAnalyticsBuilder
import com.brsanthu.googleanalytics.request.ExceptionHit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.analytics.UsageAnalytics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.spy
import org.mockito.Mockito.validateMockitoUsage
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class AnalyticsTest {
    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockResources: Resources

    private val sharedPreferences =
        SPMockBuilder().createSharedPreferences().apply {
            edit {
                putBoolean(UsageAnalytics.ANALYTICS_OPTIN_KEY, true)
            }
        }

    @Before
    fun setUp() {
        UsageAnalytics.resetForTests()
        MockitoAnnotations.openMocks(this)

        whenever((mockResources.getBoolean(R.bool.ga_anonymizeIp))).thenReturn(true)
        whenever(mockResources.getInteger(R.integer.ga_sampleFrequency))
            .thenReturn(10)
        whenever(mockContext.resources)
            .thenReturn(mockResources)
        whenever(mockContext.getString(R.string.ga_trackingId))
            .thenReturn("Mock Tracking ID")
        whenever(mockContext.getString(R.string.app_name))
            .thenReturn("Mock Application Name")
        whenever(mockContext.packageName)
            .thenReturn("mock_context")
        whenever(mockContext.getSharedPreferences("mock_context_preferences", Context.MODE_PRIVATE))
            .thenReturn(sharedPreferences)
    }

    private class SpyGoogleAnalyticsBuilder : GoogleAnalyticsBuilder() {
        override fun build(): GoogleAnalytics {
            val analytics = super.build()
            return spy(analytics)
        }
    }

    @After
    fun validate() {
        validateMockitoUsage()
    }

    @Test // TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5019
    @Suppress("DEPRECATION")
    fun testSendException() {
        mockStatic(PreferenceManager::class.java).use { _ ->
            mockStatic(GoogleAnalytics::class.java).use { _ ->
                whenever(PreferenceManager.getDefaultSharedPreferences(any()))
                    .thenReturn(sharedPreferences)
                whenever(GoogleAnalytics.builder())
                    .thenReturn(SpyGoogleAnalyticsBuilder())

                // This is actually a Mockito Spy of GoogleAnalyticsImpl
                val analytics = mockContext.let { UsageAnalytics.initialize(it) }

                // no root cause
                val exception = mock(Exception::class.java)
                whenever(exception.cause).thenReturn(null)
                val cause = UsageAnalytics.getCause(exception)
                verify(exception).cause
                assertEquals(exception, cause)

                // a 3-exception chain inside the actual analytics call
                val childException = mock(Exception::class.java)
                whenever(childException.cause).thenReturn(null)
                whenever(childException.toString()).thenReturn("child exception toString()")
                val parentException = mock(Exception::class.java)
                whenever(parentException.cause).thenReturn(childException)
                val grandparentException = mock(Exception::class.java)
                whenever(grandparentException.cause).thenReturn(parentException)

                // prepare analytics so we can inspect what happens
                val spyHit = spy(ExceptionHit())
                doReturn(spyHit).whenever(analytics).exception()
                try {
                    UsageAnalytics.sendAnalyticsException(grandparentException, false)
                } catch (e: Exception) {
                    // do nothing - this is expected because UsageAnalytics isn't fully initialized
                }
                verify(grandparentException).cause
                verify(parentException).cause
                verify(childException).cause
                verify(analytics)?.exception()
                verify(spyHit).exceptionDescription(anyString())
                verify(spyHit).sendAsync()
                assertEquals(spyHit.exceptionDescription(), "child exception toString()")
            }
        }
    }
}
