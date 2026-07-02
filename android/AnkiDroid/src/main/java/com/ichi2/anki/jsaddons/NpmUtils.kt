/*
 * Copyright (c) 2021 Mani <infinyte01@gmail.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.jsaddons

import timber.log.Timber
import java.net.URLEncoder
import java.util.Locale
import java.util.regex.Pattern

object NpmUtils {
    /**
     * Check if given string is a valid npm package name
     *
     * https://github.com/npm/validate-npm-package-name/blob/main/index.js
     * https://github.com/lassjs/is-valid-npm-name/blob/master/index.js
     */
    fun validateName(name: String): Boolean {
        if (name.isBlank()) {
            Timber.i("Invalid package name: non-empty characters allowed")
            return false
        }

        // first trim it
        if (name.trim() != name) {
            Timber.i("Invalid package name: non-empty characters allowed")
            return false
        }

        // name can no longer contain more than 214 characters
        if (name.length > 214) {
            Timber.i("Invalid package name: name string length less than 214 allowed")
            return false
        }

        // name cannot start with a period or an underscore
        if (name.startsWith(".") || name.startsWith("_")) {
            Timber.i("Invalid package name: name cannot start with a period or an underscore")
            return false
        }

        // name can no longer contain capital letters
        if (name.lowercase(Locale.getDefault()) != name) {
            Timber.i("Invalid package name: only small letters allowed")
            return false
        }

        // must have @
        // must have @ at beginning of string
        if (name.startsWith("@")) {
            // must have only one @
            if (name.indexOf('@') != name.lastIndexOf('@')) {
                Timber.i("Invalid scoped package name: only one '@' allowed")
                return false
            }

            // must have /
            if (!name.contains('/')) {
                Timber.i("Invalid scoped package name: must have '/'")
                return false
            }

            // must have only one /
            if (name.indexOf('/') != name.lastIndexOf('/')) {
                Timber.i("Invalid scoped package name: only one '/' allowed")
                return false
            }

            // validate scope
            val arr = name.split('/')
            val scope = arr[0].removePrefix("@")
            val isValidScopeName = validateName(scope)
            if (!isValidScopeName) return false

            // validate name again
            return validateName(arr[1])
        }

        // no non-URL-safe characters
        if (URLEncoder.encode(name, "UTF-8") != name) {
            Timber.i("Invalid package name: only URL safe characters allowed")
            return false
        }

        // name can no longer contain special characters ("~\'!()*")'
        val special = Pattern.compile("[~\'!()*]")
        if (special.matcher(name).find()) {
            Timber.i("Invalid package name: no special characters allowed")
            return false
        }

        return true
    }
}
