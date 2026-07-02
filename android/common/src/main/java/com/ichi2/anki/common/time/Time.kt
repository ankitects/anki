// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.time

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.sql.Date as SqlDate
import java.util.Date as UtilDate

/** Allows injection of time dependencies  */
abstract class Time {
    /** Date of this time  */
    val currentDate: UtilDate
        get() = UtilDate(intTimeMS())

    /**The time in integer seconds.  */
    fun intTime(): Long = intTimeMS() / 1000L

    abstract fun intTimeMS(): Long

    /** Calendar for this date  */
    fun calendar(): Calendar {
        val cal = Calendar.getInstance()
        cal.time = currentDate
        return cal
    }

    /**
     * Returns the effective date of the present moment.
     * If the time is prior the cut-off time (9:00am by default as of 11/02/10) return yesterday,
     * otherwise today
     * Note that the Date class is java.sql.Date whose constructor sets hours, minutes etc to zero
     *
     * @param utcOffset The UTC offset in seconds we are going to use to determine today or yesterday.
     * @return The date (with time set to 00:00:00) that corresponds to today in Anki terms
     */
    fun genToday(utcOffset: Double): SqlDate {
        // The result is not adjusted for timezone anymore, following libanki model
        // Timezone adjustment happens explicitly in Deck.updateCutoff(), but not in Deck.checkDailyStats()
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        df.timeZone = TimeZone.getTimeZone("GMT")
        val cal: Calendar = gregorianCalendar(intTimeMS() - utcOffset.toLong() * 1000L)
        return SqlDate.valueOf(df.format(cal.time))
    }

    companion object {
        fun gregorianCalendar(timeInMS: Long): GregorianCalendar {
            val calendar = GregorianCalendar()
            calendar.timeInMillis = timeInMS
            return calendar
        }

        /**
         * Calculate the UTC offset
         */
        fun utcOffset(): Double {
            // Okay to use real time, as the result does not depends on time at all here
            val cal = Calendar.getInstance()
            // 4am
            return (4 * 60 * 60 - (cal[Calendar.ZONE_OFFSET] + cal[Calendar.DST_OFFSET]) / 1000).toDouble()
        }
    }
}
