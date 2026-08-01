/*
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>
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

package com.ichi2.anki.multimediacard.fields

import com.ichi2.anki.libanki.Collection
import java.io.File

/**
 * Text Field implementation.
 */
class TextField :
    FieldBase(),
    IField {
    private var _text = ""
    private var _name: String? = null

    override val type: EFieldType = EFieldType.TEXT

    override val isModified: Boolean
        get() = thisModified

    override var mediaFile: File? = null

    override var text: String?
        get() = _text
        set(value) {
            _text = value!!
            thisModified = true
        }

    override var hasTemporaryMedia: Boolean = false

    override var name: String?
        get() = _name
        set(value) {
            _name = value
        }

    override val formattedValue: String?
        get() = text

    override fun setFormattedString(
        col: Collection,
        value: String,
    ) {
        _text = value
    }
}
