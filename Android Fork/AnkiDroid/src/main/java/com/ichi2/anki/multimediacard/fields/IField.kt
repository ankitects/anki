// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>

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
