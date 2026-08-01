/*
 * Copyright (c) 2021 mikunimaru <com.mikuni0@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import com.ichi2.anki.common.utils.indexOfOrNull
import java.util.Locale

object LanguageUtils {
    /**
     * Convert a string representation of a locale, in the format returned by Locale.toString(),
     * into a Locale object, disregarding any script and extensions fields (i.e. using solely the
     * language, country and variant fields).
     *
     *
     * Returns a Locale object constructed from an empty string if the input string is null, empty
     * or contains more than 3 fields separated by underscores.
     */
    fun localeFromStringIgnoringScriptAndExtensions(localeCodeStr: String): Locale {
        val localeCode = stripScriptAndExtensions(localeCodeStr)
        val fields = localeCode.split("_".toRegex()).toTypedArray()
        return when (fields.size) {
            1 -> Locale.forLanguageTag(fields[0])
            2 -> Locale.forLanguageTag(fields[0] + '-' + fields[1])
            3 -> Locale.forLanguageTag(fields[0] + '-' + fields[1] + '-' + fields[2])
            else -> Locale.forLanguageTag("")
        }
    }

    /**
     * @return true if the app is using a RTL language, false otherwise or if there's any error when
     *  querying the [Locale]
     */
    fun appLanguageIsRTL(): Boolean {
        val localeName = Locale.getDefault().displayName
        if (localeName.isEmpty()) return false
        val directionality = Character.getDirectionality(localeName[0])
        return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
            directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    }

    private fun stripScriptAndExtensions(localeCodeStr: String): String {
        val hashPos = localeCodeStr.indexOfOrNull('#') ?: return localeCodeStr
        return localeCodeStr.take(hashPos)
    }
}
