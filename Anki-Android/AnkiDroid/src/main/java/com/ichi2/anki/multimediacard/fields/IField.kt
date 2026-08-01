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
import java.io.Serializable

/**
 * General interface for a field of any type.
 */
interface IField : Serializable {
    val type: EFieldType

    val isModified: Boolean

    // Path of the folder containing media used by AnkiDroid.
    var mediaFile: File?

    // For Text type
    var text: String?

    /**
     * Mark if the current media path is temporary and if it should be deleted once the media has been processed.
     */
    var hasTemporaryMedia: Boolean

    var name: String?

    /**
     * Returns the formatted value for this field. Each implementation of IField should return in a format which will be
     * used to store in the database
     *
     * @return
     */
    val formattedValue: String?

    /**
     * @param col Collection - bad abstraction, used to obtain media directory only.
     * @param value The HTML to send to the field.
     */
    fun setFormattedString(
        col: Collection,
        value: String,
    )
}
