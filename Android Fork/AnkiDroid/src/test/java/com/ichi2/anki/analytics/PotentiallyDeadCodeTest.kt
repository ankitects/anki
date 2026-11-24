// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.analytics.reportPotentiallyDeadCode
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Test

class PotentiallyDeadCodeTest {
    @Before
    fun recordReports() {
        mockkObject(Analytics)
    }

    @After
    fun stopRecordingReports() {
        unmockkObject(Analytics)
    }

    @Test
    fun `analytics failure does not escape to the caller`() {
        every { Analytics.send(any()) } throws IllegalStateException("Analytics unavailable")

        reportPotentiallyDeadCode("SomeClass.fallback")
    }
}
