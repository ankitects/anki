// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.libanki.CardTemplate
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.anki.libanki.utils.insert
import com.ichi2.anki.libanki.utils.remove
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.utils.ext.readJson
import com.ichi2.anki.utils.ext.writeJson
import timber.log.Timber
import java.io.File
import java.io.IOException

/** A wrapper for a notetype in JSON format with helpers for editing the notetype. */
class CardTemplateNotetype(
    val notetype: NotetypeJson,
) {
    /** Database identity at each editor position; null for an unsaved addition. */
    private var originalTemplateOrds: ArrayList<CardOrdinal?> = notetype.templates.mapTo(ArrayList()) { it.ord }
    private var deletedTemplateOrds = ArrayList<CardOrdinal>()

    fun toBundle(): Bundle =
        Bundle().apply {
            putString(STATE_EDITED_NOTE_TYPE_FILENAME, saveTempNoteType(appContext, notetype))
            putSerializable(STATE_ORIGINAL_TEMPLATE_ORDS, originalTemplateOrds)
            putSerializable(STATE_DELETED_TEMPLATE_ORDS, deletedTemplateOrds)
        }

    private fun loadTemplateChanges(bundle: Bundle) {
        try {
            originalTemplateOrds = bundle.getSerializableCompat(STATE_ORIGINAL_TEMPLATE_ORDS)!!
            deletedTemplateOrds = bundle.getSerializableCompat(STATE_DELETED_TEMPLATE_ORDS)!!
        } catch (e: ClassCastException) {
            Timber.e(e, "Unexpected cast failure")
        }
    }

    fun getTemplate(ord: Int): CardTemplate {
        Timber.d("getTemplate() on ordinal %s", ord)
        return notetype.templates[ord]
    }

    val templateCount: Int
        get() = notetype.templates.length()

    val noteTypeId: NoteTypeId
        get() = notetype.id

    var css: String
        get() = notetype.css
        set(value) {
            notetype.css = value
        }

    fun updateTemplate(
        ordinal: Int,
        template: CardTemplate,
    ) {
        notetype.templates[ordinal] = template
    }

    fun addNewTemplate() {
        Timber.d("addNewTemplate()")
        originalTemplateOrds.add(null)
    }

    fun removeTemplate(ord: Int) {
        Timber.d("removeTemplate() on ordinal %s", ord)
        originalTemplateOrds.removeAt(ord)?.let { deletedTemplateOrds.add(it) }
    }

    /** Move a template while retaining its database identity for saving, previews and deletion checks. */
    fun repositionTemplate(
        from: CardOrdinal,
        to: CardOrdinal,
    ) {
        require(from in 0 until templateCount && to in 0 until templateCount)
        if (from == to) return
        val template = getTemplate(from)
        notetype.templates.remove(template)
        notetype.templates.insert(to, template)
        originalTemplateOrds.add(to, originalTemplateOrds.removeAt(from))
        Notetypes._updateTemplOrds(notetype)
    }

    suspend fun saveToDatabase() {
        Timber.d("saveToDatabase() called")
        val updated = notetype.deepClone()
        for ((i, template) in updated.templates.withIndex()) {
            template.setOrd(originalTemplateOrds[i])
        }
        undoableOp {
            notetypes.updateDict(updated)
        }
        clearTempNoteTypeFiles()
    }

    /** Database ordinals of pending deletions, including the proposed editor position. */
    fun getDeleteDbOrds(ord: Int): IntArray = (deletedTemplateOrds + listOfNotNull(getOriginalOrdinal(ord))).toIntArray()

    /** Database ordinal for an editor position, or null for an unsaved addition. */
    fun getOriginalOrdinal(ordinal: Int): CardOrdinal? = originalTemplateOrds[ordinal]

    companion object {
        private const val STATE_EDITED_NOTE_TYPE_FILENAME = "editedNoteTypeFilename"
        private const val STATE_ORIGINAL_TEMPLATE_ORDS = "originalTemplateOrds"
        private const val STATE_DELETED_TEMPLATE_ORDS = "deletedTemplateOrds"

        /**
         * Load the TemporaryNoteType from the filename included in a Bundle
         *
         * @param bundle a Bundle that should contain persisted JSON under STATE_EDITED_NOTE_TYPE_FILENAME key
         * @return re-hydrated TemporaryNoteType or null if there was a problem, null means should reload from database
         */
        fun fromBundle(bundle: Bundle): CardTemplateNotetype? {
            val editedNoteTypeFileName = bundle.getString(STATE_EDITED_NOTE_TYPE_FILENAME)
            // Bundle.getString is @Nullable, so we have to check.
            if (editedNoteTypeFileName == null) {
                Timber.d("fromBundle() - note type file name under key %s", STATE_EDITED_NOTE_TYPE_FILENAME)
                return null
            }
            Timber.d("onCreate() loading saved note type file %s", editedNoteTypeFileName)
            val tempNotetypeJSON: NotetypeJson =
                try {
                    getTempNoteType(editedNoteTypeFileName)
                } catch (e: IOException) {
                    Timber.w(e, "Unable to load saved note type file")
                    return null
                }
            return CardTemplateNotetype(tempNotetypeJSON).apply {
                loadTemplateChanges(bundle)
            }
        }

        /**
         * Save the current note type to a temp file in the application internal cache directory
         * @return String representing the absolute path of the saved file, or null if there was a problem
         */
        fun saveTempNoteType(
            context: Context,
            tempNoteType: NotetypeJson,
        ): String? {
            Timber.d("saveTempNoteType() saving tempNoteType")
            var tempNoteTypeFile: File
            try {
                tempNoteTypeFile = File.createTempFile("editedTemplate", ".json", context.cacheDir)
                tempNoteTypeFile.writeJson(tempNoteType.jsonObject)
            } catch (ioe: IOException) {
                Timber.e(ioe, "Unable to create+write temp file for note type")
                return null
            }
            return tempNoteTypeFile.absolutePath
        }

        /**
         * Get the note type temporarily saved into the file represented by the given path
         * @return JSONObject holding the note type, or null if there was a problem
         */
        @Throws(IOException::class)
        fun getTempNoteType(tempNoteTypeFileName: String): NotetypeJson {
            Timber.d("getTempNoteType() fetching tempNoteType %s", tempNoteTypeFileName)
            try {
                return NotetypeJson(File(tempNoteTypeFileName).readJson())
            } catch (e: IOException) {
                Timber.e(e, "Unable to read+parse tempNoteType from file %s", tempNoteTypeFileName)
                throw e
            }
        }

        /** Clear any temp note type files saved into internal cache directory  */
        fun clearTempNoteTypeFiles(): Int {
            var deleteCount = 0
            for (c in appContext.cacheDir.listFiles() ?: arrayOf()) {
                val absolutePath = c.absolutePath
                if (absolutePath.contains("editedTemplate") && absolutePath.endsWith("json")) {
                    if (!c.delete()) {
                        Timber.w("Unable to delete temp file %s", c.absolutePath)
                    } else {
                        deleteCount++
                        Timber.d("Deleted temp note type file %s", c.absolutePath)
                    }
                }
            }
            return deleteCount
        }

        fun isOrdinalPendingAdd(
            noteType: CardTemplateNotetype,
            ord: Int,
        ): Boolean = noteType.getOriginalOrdinal(ord) == null
    }
}

/**
 * Temporary file containing a [NotetypeJson]
 *
 * Useful for adding a [NotetypeJson] into a [Bundle], like when using [Intent.putExtra]
 * for sending an object to another activity.
 *
 * The notetype is written into a file because there is a
 * [limit of 1MB](https://developer.android.com/reference/android/os/TransactionTooLargeException.html)
 * for [Bundle] transactions, and notetypes can be bigger than that (#5600).
 */
class NotetypeFile(
    path: String,
) : File(path),
    Parcelable {
    /**
     * @param directory where the file will be saved
     * @param notetype to be stored
     */
    constructor(directory: File, notetype: NotetypeJson) : this(createTempFile("notetype", ".tmp", directory).absolutePath) {
        try {
            writeJson(notetype.jsonObject)
        } catch (ioe: IOException) {
            Timber.w(ioe, "Unable to create+write temp file for note type")
        }
    }

    /**
     * @param context for getting the cache directory
     * @param notetype to be stored
     */
    constructor(context: Context, notetype: NotetypeJson) : this(context.cacheDir, notetype)

    fun getNotetype(): NotetypeJson =
        try {
            NotetypeJson(readJson())
        } catch (e: IOException) {
            Timber.w(e, "Unable to read+parse tempNoteType from file %s", absolutePath)
            throw e
        }

    /**
     * Returns the notetype, or `null` if the backing file can't be read (e.g. the temp
     * file was cleaned up by the OS after process death, or the user cleared app data).
     */
    fun getNotetypeOrNull(): NotetypeJson? =
        try {
            getNotetype()
        } catch (e: IOException) {
            Timber.d(e, "Failed to read notetype")
            null
        }

    override fun describeContents(): Int = 0

    override fun writeToParcel(
        dest: Parcel,
        flags: Int,
    ) {
        dest.writeString(path)
    }

    companion object {
        @JvmField
        @Suppress("unused")
        val CREATOR =
            object : Parcelable.Creator<NotetypeFile> {
                override fun createFromParcel(source: Parcel?): NotetypeFile = NotetypeFile(source!!.readString()!!)

                override fun newArray(size: Int): Array<NotetypeFile> = arrayOf()
            }
    }
}
