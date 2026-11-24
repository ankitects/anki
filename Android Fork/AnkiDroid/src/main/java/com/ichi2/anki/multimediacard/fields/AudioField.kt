// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>

package com.ichi2.anki.multimediacard.fields

import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.requireMediaFolder
import java.io.File
import java.util.regex.Pattern

/**
 * Implementation of Audio field types
 */
abstract class AudioField :
    FieldBase(),
    IField {
    override var mediaFile: File? = null
        set(value) {
            field = value
            thisModified = true
        }

    override var text: String? = null

    override var hasTemporaryMedia: Boolean = false

    override val formattedValue: String
        get() =
            mediaFile?.let { file ->
                if (file.exists()) "[sound:${file.name}]" else ""
            } ?: ""

    override fun setFormattedString(
        col: Collection,
        value: String,
    ) {
        val p = Pattern.compile(PATH_REGEX)
        val m = p.matcher(value)
        var mediaFileName = ""
        if (m.find()) {
            mediaFileName = m.group(1)!!
        }
        mediaFile = File(col.requireMediaFolder(), mediaFileName)
    }

    companion object {
        protected const val PATH_REGEX = "\\[sound:(.*)]"
    }
}
