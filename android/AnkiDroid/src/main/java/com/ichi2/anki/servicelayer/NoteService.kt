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

package com.ichi2.anki.servicelayer

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.FieldEditText
import com.ichi2.anki.exception.MediaSizeLimitExceededException
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.fields.EFieldType
import com.ichi2.anki.multimediacard.fields.IField
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.multimediacard.fields.MediaClipField
import com.ichi2.anki.multimediacard.fields.TextField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.anki.observability.undoableOp
import net.ankiweb.rsdroid.Backend
import org.json.JSONException
import timber.log.Timber
import java.io.File
import java.io.IOException

object NoteService {
    /**
     * Creates an empty Note from given Model
     *
     * @param model the model in JSONObject format
     * @return a new MultimediaEditableNote instance
     */
    fun createEmptyNote(model: NotetypeJson): MultimediaEditableNote {
        val note = MultimediaEditableNote()
        try {
            val fieldsArray = model.fields
            note.setNumFields(fieldsArray.length())
            for ((i, field) in fieldsArray.withIndex()) {
                val uiTextField =
                    TextField().apply {
                        name = field.name
                        text = field.name
                    }
                note.setField(i, uiTextField)
            }
            note.noteTypeId = model.id
        } catch (e: JSONException) {
            Timber.w(e, "Error parsing model: %s", model)
            // Return note with default/empty fields
        }
        return note
    }

    fun updateMultimediaNoteFromFields(
        col: Collection,
        fields: Array<String>,
        noteTypeId: NoteTypeId,
        mmNote: MultimediaEditableNote,
    ) {
        for (i in fields.indices) {
            val value = fields[i]
            val field: IField =
                if (value.startsWith("<img")) {
                    ImageField()
                } else if (value.startsWith("[sound:") && value.contains("rec")) {
                    AudioRecordingField()
                } else if (value.startsWith("[sound:")) {
                    MediaClipField()
                } else {
                    TextField()
                }
            field.setFormattedString(col, value)
            mmNote.setField(i, field)
        }
        mmNote.noteTypeId = noteTypeId
        mmNote.freezeInitialFieldValues()
        // TODO: set current id of the note as well
    }

    /**
     * Updates the JsonNote field values from MultimediaEditableNote When both notes are using the same Model, it updates
     * the destination field values with source values. If models are different it throws an Exception
     *
     * @param noteSrc
     * @param editorNoteDst
     */
    fun updateJsonNoteFromMultimediaNote(
        noteSrc: IMultimediaEditableNote?,
        editorNoteDst: Note,
    ) {
        if (noteSrc is MultimediaEditableNote) {
            if (noteSrc.noteTypeId != editorNoteDst.noteTypeId) {
                throw RuntimeException("Source and Destination Note ID do not match.")
            }
            val totalFields: Int = noteSrc.numberOfFields
            for (i in 0 until totalFields) {
                editorNoteDst.values()[i] = noteSrc.getField(i)!!.formattedValue!!
            }
        }
    }

    /**
     * Considering the field is new, if it has media handle it
     *
     * @param field
     *
     * @throws IOException
     * @throws OutOfMemoryError if the file could not be copied to a contiguous block of memory (or is >= 2GB)
     */
    fun importMediaToDirectory(
        col: Collection,
        field: IField,
        skipSizeCheck: Boolean = false,
    ) {
        val file = field.mediaFile ?: return

        // > is the correct check: https://github.com/ankitects/anki/blob/5d9d864514b9a4ac7d4688fac390c22db91d4abe/rslib/src/sync/media/upload.rs#L87
        // TODO: Move this check to the backend
        if (!skipSizeCheck && file.length() > Backend.MAX_INDIVIDUAL_MEDIA_FILE_SIZE) {
            throw MediaSizeLimitExceededException(
                file.name,
                file.length(),
                Backend.MAX_INDIVIDUAL_MEDIA_FILE_SIZE,
            )
        }

        val inFile =
            when (field.type) {
                EFieldType.AUDIO_RECORDING, EFieldType.MEDIA_CLIP, EFieldType.IMAGE -> field.mediaFile
                EFieldType.TEXT -> null
            } ?: return

        if (inFile.length() == 0L) return

        val fname = col.media.addFile(inFile)
        val outFile = File(col.media.dir, fname)
        Timber.v("""File "%s" should be copied to "%s""", fname, outFile)
        if (field.hasTemporaryMedia && outFile != inFile) {
            inFile.delete()
        }
        field.mediaFile = outFile
    }

    fun convertToHtmlNewline(
        fieldData: String,
        replaceNewlines: Boolean,
    ): String =
        if (!replaceNewlines) {
            fieldData
        } else {
            fieldData.replace(FieldEditText.NEW_LINE, "<br>")
        }

    suspend fun toggleMark(
        note: Note,
        handler: Any? = null,
    ) {
        if (isMarked(note)) {
            note.removeTag("marked")
        } else {
            note.addTag("marked")
        }

        undoableOp(handler) {
            updateNote(note)
        }
    }

    suspend fun isMarked(note: Note): Boolean = withCol { isMarked(this, note) }

    fun isMarked(
        col: Collection,
        note: Note,
    ): Boolean = note.hasTag(col, tag = "marked")

    interface NoteField {
        val ord: Int

        // ideally shouldn't be nullable
        val fieldText: String?
    }
}

const val MARKED_TAG = "marked"

suspend fun isBuryNoteAvailable(card: Card): Boolean =
    withCol {
        db.queryScalar(
            "select 1 from cards where nid = ? and id != ? and queue >=  " + QueueType.New.code + " limit 1",
            card.nid,
            card.id,
        ) == 1
    }

suspend fun isSuspendNoteAvailable(card: Card): Boolean =
    withCol {
        db.queryScalar(
            "select 1 from cards where nid = ? and id != ? and queue != " + QueueType.Suspended.code + " limit 1",
            card.nid,
            card.id,
        ) == 1
    }
