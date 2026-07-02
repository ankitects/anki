// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

/**
 * A type-safe wrapper representing the sampling rate for Google Analytics (GA) events.
 *
 * This defines the percentage of events sent to GA, while the remainder are dropped
 * client-side to limit reporting volume. It is backed by an [Int] ranging from `0..100`.
 *
 * The [Uninitialized] state serves as a sentinel value indicating that the rate has not
 * yet been loaded from resources, eliminating the need for callers to handle custom
 * out-of-band integers for this state.
 */
@JvmInline
value class AnalyticsSamplePercentage(
    val value: Int,
) {
    init {
        require(value == UNINITIALIZED_VALUE || value in 0..100) {
            "Analytics sample percentage must be in 0..100 or Uninitialized (-1), got $value"
        }
    }

    /** `true` once the percentage has been loaded from resources or set explicitly. */
    val isInitialized: Boolean get() = value != UNINITIALIZED_VALUE

    companion object {
        private const val UNINITIALIZED_VALUE = -1

        /** Sentinel: not yet loaded from resources. */
        val Uninitialized = AnalyticsSamplePercentage(UNINITIALIZED_VALUE)

        /** 100% of events are sent; used by [AnkiDroidUsageAnalytics.setDevMode]. */
        val Full = AnalyticsSamplePercentage(100)
    }
}
