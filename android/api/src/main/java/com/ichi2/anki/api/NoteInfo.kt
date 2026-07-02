// SPDX-FileCopyrightText: Copyright (c) 2016 Timothy Rae <perceptualchaos2@gmail.com>
// SPDX-License-Identifier: LGPL-3.0-or-later
package com.ichi2.anki.api

import android.database.Cursor
import com.ichi2.anki.FlashCardsContract

/**
 * Representation of the contents of a note in AnkiDroid.
 */
@Suppress("unused")
public class NoteInfo {
    private val id: Long
    private val fields: Array<String>
    private val tags: Set<String>

    internal companion object {
        /**
         * Static initializer method to build a NoteInfo object from a Cursor
         * @param cursor from a query to FlashCardsContract.Note.CONTENT_URI
         * @return a NoteInfo object or null if the cursor was not valid
         */
        @JvmStatic // API Project
        internal fun buildFromCursor(cursor: Cursor): NoteInfo? =
            try {
                val idIndex = cursor.getColumnIndexOrThrow(FlashCardsContract.Note._ID)
                val fldsIndex = cursor.getColumnIndexOrThrow(FlashCardsContract.Note.FLDS)
                val tagsIndex = cursor.getColumnIndexOrThrow(FlashCardsContract.Note.TAGS)
                val fields = Utils.splitFields(cursor.getString(fldsIndex))
                val id = cursor.getLong(idIndex)
                val tags: Set<String> = HashSet(listOf(*Utils.splitTags(cursor.getString(tagsIndex))))
                NoteInfo(id, fields, tags)
            } catch (e: Exception) {
                null
            }
    }

    private constructor(id: Long, fields: Array<String>, tags: Set<String>) {
        this.id = id
        this.fields = fields
        this.tags = tags
    }

    /**
     * Clone a NoteInfo object
     * @param parent the object to clone
     */
    public constructor(parent: NoteInfo) {
        id = parent.id
        fields = parent.fields.clone()
        tags = HashSet(parent.tags)
    }

    /** Note ID  */
    public fun getId(): Long = id

    /** The array of fields  */
    public fun getFields(): Array<String> = fields

    /** The set of tags  */
    public fun getTags(): Set<String> = tags

    /** The first field  */
    public fun getKey(): String = getFields()[0]
}
