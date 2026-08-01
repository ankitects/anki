/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.noteeditor

import android.content.Context
import android.os.Bundle
import android.view.View
import com.ichi2.anki.FieldEditLine
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.R
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.libanki.Field
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.Notetypes
import com.ichi2.utils.MapUtil.getKeyByValue
import kotlin.math.min

/** Responsible for recreating EditFieldLines after NoteEditor operations
 * This primarily exists so we can use saved instance state to repopulate the dynamically created FieldEditLine
 */
class FieldState private constructor(
    private val editor: NoteEditorFragment,
) {
    private var customViewIds: List<Int>? = null

    fun loadFieldEditLines(type: FieldChangeType): List<FieldEditLine> {
        val fieldEditLines: List<FieldEditLine> =
            if (type.type == Type.INIT && customViewIds != null) {
                recreateFields(customViewIds!!)
            } else {
                createFields(type).also { fields ->
                    for (field in fields) {
                        field.id = View.generateViewId()
                    }
                }
            }

        if (type.type == Type.CLEAR_KEEP_STICKY) {
            // we use the UI values here as the model will post-processing steps (newline -> br).
            val currentFieldStrings = editor.currentFieldStrings
            for ((fldIdx, field) in editor.currentFields.withIndex()) {
                if (field.sticky) {
                    fieldEditLines[fldIdx].setContent(currentFieldStrings[fldIdx], type.replaceNewlines)
                }
            }
        }
        if (type.type == Type.CHANGE_FIELD_COUNT) {
            val currentFieldStrings = editor.currentFieldStrings
            for (i in 0 until min(currentFieldStrings.size, fieldEditLines.size)) {
                fieldEditLines[i].setContent(currentFieldStrings[i], type.replaceNewlines)
            }
        }
        return fieldEditLines
    }

    /**
     * Given a list of [viewIds]: create the fields, assign the IDs and let Android
     * restore the state via [FieldEditLine.onRestoreInstanceState]
     */
    private fun recreateFields(viewIds: List<Int>): List<FieldEditLine> =
        viewIds.map { id -> FieldEditLine(editor.requireContext()).also { field -> field.id = id } }

    private fun createFields(type: FieldChangeType): List<FieldEditLine> {
        val fields = getFields(type)
        val editLines: MutableList<FieldEditLine> = ArrayList(fields.size)
        for (i in fields.indices) {
            val editLineView = FieldEditLine(editor.requireContext())
            editLines.add(editLineView)
            editLineView.name = fields[i][0]
            editLineView.setContent(fields[i][1], type.replaceNewlines)
            editLineView.setOrd(i)
        }
        return editLines
    }

    private fun getFields(type: FieldChangeType): Array<Array<String>> {
        if (type.type == Type.REFRESH_WITH_MAP) {
            val items = editor.fieldsFromSelectedNote
            val fMapNew = Notetypes.fieldMap(type.newNotetype!!)
            return fromFieldMap(editor.requireContext(), items, fMapNew, type.modelChangeFieldMap!!)
        }
        return editor.fieldsFromSelectedNote
    }

    fun setInstanceState(savedInstanceState: Bundle?) {
        customViewIds = savedInstanceState?.getIntegerArrayList("customViewIds")
    }

    /** How fields should be changed when the UI is rebuilt  */
    class FieldChangeType(
        val type: Type,
        val replaceNewlines: Boolean,
    ) {
        var modelChangeFieldMap: Map<Int, Int>? = null
        var newNotetype: NotetypeJson? = null

        companion object {
            fun refreshWithMap(
                newNotetype: NotetypeJson?,
                modelChangeFieldMap: Map<Int, Int>?,
                replaceNewlines: Boolean,
            ): FieldChangeType {
                val typeClass = FieldChangeType(Type.REFRESH_WITH_MAP, replaceNewlines)
                typeClass.newNotetype = newNotetype
                typeClass.modelChangeFieldMap = modelChangeFieldMap
                return typeClass
            }

            fun refresh(replaceNewlines: Boolean): FieldChangeType = fromType(Type.REFRESH, replaceNewlines)

            fun refreshWithStickyFields(replaceNewlines: Boolean): FieldChangeType = fromType(Type.CLEAR_KEEP_STICKY, replaceNewlines)

            fun changeFieldCount(replaceNewlines: Boolean): FieldChangeType = fromType(Type.CHANGE_FIELD_COUNT, replaceNewlines)

            fun onActivityCreation(replaceNewlines: Boolean): FieldChangeType = fromType(Type.INIT, replaceNewlines)

            fun onNoteAdded(replaceNewlines: Boolean): FieldChangeType = fromType(Type.NOTE_ADDED, replaceNewlines)

            private fun fromType(
                type: Type,
                replaceNewlines: Boolean,
            ): FieldChangeType = FieldChangeType(type, replaceNewlines)
        }
    }

    enum class Type {
        INIT,
        CLEAR_KEEP_STICKY,
        CHANGE_FIELD_COUNT,
        REFRESH,
        REFRESH_WITH_MAP,
        NOTE_ADDED,
    }

    companion object {
        private fun allowFieldRemapping(oldFields: Array<Array<String>>): Boolean = oldFields.size > 2

        fun fromEditor(editor: NoteEditorFragment): FieldState = FieldState(editor)

        @KotlinCleanup("speed - no need for arrayOfNulls")
        private fun fromFieldMap(
            context: Context,
            oldFields: Array<Array<String>>,
            fMapNew: Map<String, Pair<Int, Field>>,
            modelChangeFieldMap: Map<Int, Int>,
        ): Array<Array<String>> {
            // Build array of label/values to provide to field EditText views
            val fields = Array(fMapNew.size) { arrayOfNulls<String>(2) }
            for (fname in fMapNew.keys) {
                val fieldPair = fMapNew[fname] ?: continue
                // Field index of new note type
                val i = fieldPair.first
                // Add values from old note type if they exist in map, otherwise make the new field empty
                if (modelChangeFieldMap.containsValue(i)) {
                    // Get index of field from old note type given the field index of new note type
                    val j = getKeyByValue(modelChangeFieldMap, i) ?: continue
                    // Set the new field label text
                    if (allowFieldRemapping(oldFields)) {
                        // Show the content of old field if remapping is enabled
                        fields[i][0] = String.format(context.resources.getString(R.string.field_remapping), fname, oldFields[j][0])
                    } else {
                        fields[i][0] = fname
                    }

                    // Set the new field label value
                    fields[i][1] = oldFields[j][1]
                } else {
                    // No values from old note type exist in the mapping
                    fields[i][0] = fname
                    fields[i][1] = ""
                }
            }
            return fields.map { it.requireNoNulls() }.toTypedArray()
        }
    }
}
