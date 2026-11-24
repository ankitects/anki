// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>

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
