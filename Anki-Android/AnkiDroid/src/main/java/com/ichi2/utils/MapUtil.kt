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
