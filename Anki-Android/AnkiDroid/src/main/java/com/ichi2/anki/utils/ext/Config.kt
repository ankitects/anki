/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.utils.ext

import anki.config.ConfigKey
import com.ichi2.anki.libanki.Config

// Extensions for com.ichi2.anki.libanki.Config

/**
 * When enabled, simple text searches automatically ignore accents.
 *
 * When enabled, both 'uber' and 'über' match `["uber", "über", "Über"]`.
 *
 * [Manual: Searching - Ignoring accents/combining characters](https://docs.ankiweb.net/searching.html#ignoring-accentscombining-characters)
 *
 * [Added in Anki#1667](https://github.com/ankitects/anki/pull/1667)
 */
var Config.ignoreAccentsInSearch
    get() = getBool(ConfigKey.Bool.IGNORE_ACCENTS_IN_SEARCH)
    set(value) = setBool(ConfigKey.Bool.IGNORE_ACCENTS_IN_SEARCH, value)
