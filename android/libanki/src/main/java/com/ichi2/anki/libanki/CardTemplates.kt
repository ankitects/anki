/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.libanki

import com.ichi2.anki.common.json.JSONContainer
import com.ichi2.anki.libanki.utils.NotInPyLib
import org.json.JSONArray
import org.json.JSONObject

/**
 * A collection of [CardTemplate]
 *
 * @see NotetypeJson.templates
 */
@JvmInline
@NotInPyLib
value class CardTemplates(
    override val jsonArray: JSONArray,
) : JSONContainer<CardTemplate> {
    override fun constructor(obj: JSONObject) = CardTemplate(obj)
}
