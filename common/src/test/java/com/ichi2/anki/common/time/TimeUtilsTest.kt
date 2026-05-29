// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Dhanush Sugganahalli <dhanush41230@gmail.com>

package com.ichi2.anki.common.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TimeUtilsTest {
    @Test
    fun getTimeStamp_returnsTimeStampAsUTC() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val time = MockTime(2020, 7, 7, 7, 0, 0, 0, 0)
            assertEquals("20200807070000", getTimestamp(time))
        } finally {
            TimeZone.setDefault(null)
        }
    }

    @Test
    fun formatAsString_zeroDuration_returnsZeroString() {
        val duration = 0.milliseconds
        assertEquals("00:00.00", duration.formatAsString())
    }

    @Test
    fun formatAsString_oneMinuteThirtySeconds_returnsOneMinuteThirtySecondsString() {
        val duration = 1.minutes + 30.seconds + 500.milliseconds
        assertEquals("01:30.50", duration.formatAsString())
    }

    @Test
    fun formatAsString_oneHourDuration_returnsOneHourString() {
        val duration = 1.hours
        assertEquals("01:00:00.00", duration.formatAsString())
    }

    @Test
    fun formatAsString_overOneHour_returnsHourMinuteSecondString() {
        val duration = 1.hours + 3.minutes + 4.seconds + 500.milliseconds
        assertEquals("01:03:04.50", duration.formatAsString())
    }

    @Test
    fun getDayStart_BeforeFourAM_returnsDayStart() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val input = MockTime(2020, 7, 7, 3, 0, 0, 0, 0)
            val expected = MockTime(2020, 7, 6, 4, 0, 0, 0, 0).calendar().timeInMillis
            assertEquals(expected, getDayStart(input))
        } finally {
            TimeZone.setDefault(null)
        }
    }

    @Test
    fun getDayStart_AfterFourAM_returnsDayStart() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val input = MockTime(2020, 7, 7, 5, 0, 0, 0, 0)
            val expected = MockTime(2020, 7, 7, 4, 0, 0, 0, 0).calendar().timeInMillis
            assertEquals(expected, getDayStart(input))
        } finally {
            TimeZone.setDefault(null)
        }
    }

    @Test
    fun getDayStart_AtFourAM_returnsDayStart() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val input = MockTime(2020, 7, 7, 4, 0, 0, 0, 0)
            val expected = MockTime(2020, 7, 7, 4, 0, 0, 0, 0).calendar().timeInMillis
            assertEquals(expected, getDayStart(input))
        } finally {
            TimeZone.setDefault(null)
        }
    }

    @Test
    fun getDayStart_AtJanuary1_returnsDayStart() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val input = MockTime(2021, Calendar.JANUARY, 1, 3, 0, 0, 0, 0)
            val expected = MockTime(2020, Calendar.DECEMBER, 31, 4, 0, 0, 0, 0).calendar().timeInMillis
            assertEquals(expected, getDayStart(input))
        } finally {
            TimeZone.setDefault(null)
        }
    }
}
