// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.time

import androidx.annotation.VisibleForTesting
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** @param [step] Number of milliseconds between each call.
 * @param [initTime]: Time since epoch in MS. */
@VisibleForTesting(otherwise = VisibleForTesting.NONE)
open class MockTime(
    initTime: Long,
    private val step: Int = 0,
) : Time() {
    protected var time = initTime
        private set

    /** create a mock time whose initial value is this date. Month is 0-based, in order to stay close to calendar. MS are 0. */
    constructor(
        year: Int,
        month: Int,
        date: Int,
        hourOfDay: Int,
        minute: Int,
        second: Int,
        milliseconds: Int,
        step: Int,
    ) : this(
        timeStamp(year, month, date, hourOfDay, minute, second, milliseconds),
        step,
    )

    /** Time in millisecond since epoch.  */
    override fun intTimeMS(): Long {
        val time = time
        this.time += step.toLong()
        return time
    }

    /** Add ms milliseconds  */
    private fun addMs(ms: Long) {
        time += ms
    }

    /** add s seconds  */
    private fun addS(s: Long) {
        addMs(s * 1000L)
    }

    /** add m minutes  */
    fun addM(m: Long) {
        addS(m * 60)
    }

    /** add h hours */
    private fun addH(h: Long) {
        addM(h * 60)
    }

    /** add d days */
    fun addD(d: Long) {
        addH(d * 24)
    }

    companion object {
        /**
         * Allow to get a timestamp which is independent of place where test occurs.
         * @param year Year
         * @param month Month, 0-based
         * @param date, day of month
         * @param hourOfDay, hour, from 0 to 23
         * @param minute, from 0 to 59
         * @param second, From 0 to 59
         * @param milliseconds, from 0 to 999
         * @return the time stamp of this instant in GMT calendar
         */
        fun timeStamp(
            year: Int,
            month: Int,
            date: Int,
            hourOfDay: Int,
            minute: Int,
            second: Int,
            milliseconds: Int = 0,
        ): Long {
            val timeZone = TimeZone.getTimeZone("GMT")
            val gregorianCalendar: Calendar = GregorianCalendar(year, month, date, hourOfDay, minute, second)
            gregorianCalendar.timeZone = timeZone
            gregorianCalendar[Calendar.MILLISECOND] = milliseconds
            return gregorianCalendar.timeInMillis
        }
    }
}
