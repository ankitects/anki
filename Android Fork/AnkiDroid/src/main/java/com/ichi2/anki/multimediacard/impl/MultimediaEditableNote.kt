// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>

package com.ichi2.anki.multimediacard.impl

import android.os.Parcel
import android.os.Parcelable
import com.ichi2.anki.compat.readBooleanCompat
import com.ichi2.anki.compat.writeBooleanCompat
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.fields.IField
import com.ichi2.anki.utils.ext.readSerializableList
import com.ichi2.anki.utils.ext.writeSerializableList
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import org.acra.util.IOUtils

/**
 * Implementation of the editable note.
 *
 * Has to be translate to and from anki db format.
 *
 * All variables must be handled manually by Parcelable
 */
@Parcelize
class MultimediaEditableNote() :
    IMultimediaEditableNote,
    Parcelable {
    internal constructor(
        isModified: Boolean,
        noteTypeId: Long,
        initialFields: List<IField?>?,
        fields: List<IField?>?,
    ) : this() {
        this.isModified = isModified
        this.noteTypeId = noteTypeId
        this.initialFields = initialFields?.let { ArrayList(it) }
        this.fields = fields?.let { ArrayList(it) }
    }

    override var isModified = false
        private set
    private var fields: ArrayList<IField?>? = null
    var noteTypeId: NoteTypeId = 0

    /**
     * Field values in the note editor, before any editing has taken place
     * These values should not be modified
     */
    private var initialFields: ArrayList<IField?>? = null

    private fun setThisModified() {
        isModified = true
    }

    // package
    fun setNumFields(numberOfFields: Int) {
        fieldsPrivate.clear()
        for (i in 0 until numberOfFields) {
            fieldsPrivate.add(null)
        }
    }

    private val fieldsPrivate: ArrayList<IField?>
        get() {
            if (fields == null) {
                fields = ArrayList(0)
            }
            return fields!!
        }
    override val numberOfFields: Int
        get() = fieldsPrivate.size

    override fun getField(index: Int): IField? =
        if (index in 0 until numberOfFields) {
            fieldsPrivate[index]
        } else {
            null
        }

    override fun setField(
        index: Int,
        field: IField?,
    ): Boolean {
        if (index in 0 until numberOfFields) {
            // If the same unchanged field is set.
            if (getField(index) === field) {
                if (field!!.isModified) {
                    setThisModified()
                }
            } else {
                setThisModified()
            }
            fieldsPrivate[index] = field
            return true
        }
        return false
    }

    fun freezeInitialFieldValues() {
        initialFields = ArrayList()
        for (f in fields!!) {
            initialFields!!.add(cloneField(f))
        }
    }

    override val initialFieldCount: Int
        get() = initialFields!!.size

    override fun getInitialField(index: Int): IField? = cloneField(initialFields!![index])

    private fun cloneField(f: IField?): IField? = IOUtils.deserialize(IField::class.java, IOUtils.serialize(f!!))

    val isEmpty: Boolean
        get() = fields.isNullOrEmpty()

    companion object : Parceler<MultimediaEditableNote> {
        override fun create(parcel: Parcel): MultimediaEditableNote =
            MultimediaEditableNote(
                isModified = parcel.readBooleanCompat(),
                noteTypeId = parcel.readLong(),
                initialFields = parcel.readSerializableList<IField>(),
                fields = parcel.readSerializableList<IField>(),
            )

        override fun MultimediaEditableNote.write(
            parcel: Parcel,
            flags: Int,
        ) {
            parcel.writeBooleanCompat(isModified)
            parcel.writeLong(noteTypeId)
            parcel.writeSerializableList<IField>(initialFields)
            parcel.writeSerializableList<IField>(fields)
        }
    }
}
