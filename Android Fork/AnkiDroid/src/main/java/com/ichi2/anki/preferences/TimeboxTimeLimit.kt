// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Domain representation of a review timebox time limit.
 *
 * A limit of 0 minutes represents disabled timeboxing.
 * A limit greater than 0 represents active timeboxing with the given duration in minutes.
 */
@JvmInline
value class TimeboxTimeLimit(
    val minutes: Int,
) {
    init {
        require(minutes >= 0) { "Timebox limit minutes must be non-negative: $minutes" }
    }

    val isEnabled: Boolean
        get() = minutes > 0

    /**
     * Converts this limit to a [Duration].
     *
     * Reference: Reviewer preference mapping in Anki Desktop:
     * [qt/aqt/preferences.py#L147](https://github.com/ankitects/anki/blob/main/qt/aqt/preferences.py#L147)
     */
    val duration: Duration
        get() = minutes.toDuration(DurationUnit.MINUTES)

    /** Returns this limit if [isEnabled], or [fallback] if disabled. */
    fun orElse(fallback: TimeboxTimeLimit): TimeboxTimeLimit = if (isEnabled) this else fallback

    /** Returns `true` if this limit can be decremented within [INPUT_RANGE]. */
    val canDecrement: Boolean
        get() = canDecrement(minutes)

    /** Returns `true` if this limit can be incremented within [INPUT_RANGE]. */
    val canIncrement: Boolean
        get() = canIncrement(minutes)

    companion object {
        /**
         * Arbitrary initial duration (in minutes) chosen by AnkiDroid when timeboxing is toggled
         * from disabled (0 minutes) to enabled.
         */
        const val DEFAULT_TIMEBOX_MINUTES = 20

        /**
         * Minimum active timebox duration in minutes (0 represents disabled).
         */
        const val MIN_TIMEBOX_MINUTES = 1

        /**
         * Maximum allowed timebox duration in minutes.
         *
         * Sourced from Anki Desktop's QSpinBox configuration:
         * [qt/aqt/forms/preferences.ui#L368-L370](https://github.com/ankitects/anki/blob/main/qt/aqt/forms/preferences.ui#L368-L370)
         */
        const val MAX_TIMEBOX_MINUTES = 9999

        /** Valid input range for user-entered active timebox minutes. */
        val INPUT_RANGE = MIN_TIMEBOX_MINUTES..MAX_TIMEBOX_MINUTES

        val DEFAULT = TimeboxTimeLimit(DEFAULT_TIMEBOX_MINUTES)

        /**
         * Represents disabled timeboxing (0 minutes).
         *
         * Sourced from Anki Desktop's configuration:
         * [rslib/src/config/mod.rs#L118](https://github.com/ankitects/anki/blob/main/rslib/src/config/mod.rs#L118)
         */
        val DISABLED = TimeboxTimeLimit(0)

        fun fromDuration(duration: Duration): TimeboxTimeLimit {
            val mins = duration.toInt(DurationUnit.MINUTES)
            return TimeboxTimeLimit(mins)
        }

        /**
         * Creates a [TimeboxTimeLimit] if [minutes] falls within [INPUT_RANGE],
         * or returns `null` if the value is out of bounds.
         */
        fun fromMinutes(minutes: Int): TimeboxTimeLimit? {
            if (minutes !in INPUT_RANGE) return null
            return TimeboxTimeLimit(minutes)
        }

        /** Returns `true` if [minutes] is within [INPUT_RANGE]. */
        fun isValid(minutes: Int): Boolean = minutes in INPUT_RANGE

        /**
         * Creates a [TimeboxTimeLimit] if [minutes] falls within [INPUT_RANGE],
         * or returns `null` if the value is out of bounds.
         */
        fun fromValidInput(minutes: Int): TimeboxTimeLimit? = fromMinutes(minutes)

        /** Returns `true` if [minutes] can be decremented within [INPUT_RANGE]. */
        fun canDecrement(minutes: Int): Boolean = minutes > MIN_TIMEBOX_MINUTES

        /** Returns `true` if [minutes] can be incremented within [INPUT_RANGE]. */
        fun canIncrement(minutes: Int): Boolean = minutes < MAX_TIMEBOX_MINUTES
    }
}

/** Converts this [Duration] to a [TimeboxTimeLimit]. */
fun Duration.toTimeboxTimeLimit(): TimeboxTimeLimit = TimeboxTimeLimit.fromDuration(this)
