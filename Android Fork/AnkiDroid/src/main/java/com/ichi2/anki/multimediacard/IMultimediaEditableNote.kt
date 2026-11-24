// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>

package com.ichi2.anki.multimediacard

import com.ichi2.anki.multimediacard.fields.IField
import java.io.Serializable

/**
 * Interface for a note, which multimedia card editor can process.
 */
interface IMultimediaEditableNote : Serializable {
    val numberOfFields: Int

    fun getField(index: Int): IField?

    fun setField(
        index: Int,
        field: IField?,
    ): Boolean

    val isModified: Boolean
    val initialFieldCount: Int

    fun getInitialField(index: Int): IField?
}
