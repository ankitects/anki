// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.analytics

/**
 * Converts numeric and boolean preference values to [AnalyticsEvent.SettingChanged] values.
 * Strings that do not represent integers and unsupported values are omitted.
 */
fun getPreferenceReportableValue(value: Any?): Int? =
    when (value) {
        is Int -> value
        is String ->
            try {
                value.toInt()
            } catch (_: NumberFormatException) {
                null
            }
        is Boolean -> if (value) 1 else 0
        is Float -> value.toInt()
        is Long -> value.toInt()
        else -> null
    }
