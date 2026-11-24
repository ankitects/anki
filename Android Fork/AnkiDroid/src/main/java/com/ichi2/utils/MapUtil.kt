// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

object MapUtil {
    /**
     * Convenience method for getting the corresponding key given the value in a 1-to-1 map
     * @param map map containing 1-to-1 key/value pairs
     * @param value value to get key for
     * @return key corresponding to the given value
     */
    fun <T, E> getKeyByValue(
        map: Map<T, E>,
        value: E,
    ): T? {
        for ((key, value1) in map) {
            if (value == value1) {
                return key
            }
        }
        return null
    }
}
