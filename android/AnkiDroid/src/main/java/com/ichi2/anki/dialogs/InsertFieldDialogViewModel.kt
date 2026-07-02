/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.dialogs

import android.os.Parcelable
import androidx.annotation.CheckResult
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.common.utils.ellipsize
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.model.FieldName
import com.ichi2.anki.model.SpecialFields
import com.ichi2.anki.utils.ext.asVar
import com.ichi2.anki.utils.ext.require
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.parcelize.Parcelize
import timber.log.Timber

private typealias SpecialFieldModel = com.ichi2.anki.model.SpecialField

/**
 * ViewModel for [InsertFieldDialog]
 *
 * Handles availability of fields
 */
class InsertFieldDialogViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    var currentTabFlow: MutableStateFlow<Tab> =
        savedStateHandle.getMutableStateFlow(STATE_TAB, Tab.FIELDS)

    var currentTab by currentTabFlow.asVar()

    /** The field names of the note type */
    val fieldNames = savedStateHandle.require<ArrayList<String>>(KEY_FIELD_ITEMS).map(::FieldName)

    /**
     * State of the selected card when the screen was opened
     *
     * Used for providing [special fields][SpecialFields] with the output they'd produce.
     */
    val metadata = savedStateHandle.require<InsertFieldMetadata>(KEY_INSERT_FIELD_METADATA)

    val selectedFieldFlow = MutableStateFlow<SelectedField?>(null)

    /**
     * An ordered list of special fields which may be used
     *
     * @see com.ichi2.anki.model.SpecialField
     */
    val specialFields = SpecialFields.all(side = metadata.side)

    /**
     * Select a named field defined on the note type
     */
    fun selectNamedField(fieldName: FieldName) {
        Timber.i("selected named field")
        if (!fieldNames.contains(fieldName)) return
        selectedFieldFlow.value = SelectedField.NoteTypeField.from(fieldName)
    }

    /**
     * Select a usable special field
     */
    fun selectSpecialField(field: SpecialFieldModel) {
        Timber.i("selected special field: %s", field.name)
        if (!specialFields.contains(field)) return
        selectedFieldFlow.value = SelectedField.SpecialField(model = field)
    }

    sealed class SelectedField {
        /**
         * A field defined on the note type
         *
         * e.g `Front`
         */
        class NoteTypeField(
            val name: FieldName,
        ) : SelectedField() {
            override fun renderToTemplateTag(): String = "{{$name}}"

            companion object {
                fun from(fieldName: FieldName) = NoteTypeField(fieldName)
            }
        }

        class SpecialField(
            val model: SpecialFieldModel,
        ) : SelectedField() {
            override fun renderToTemplateTag(): String = "{{${model.name}}}"
        }

        /**
         * Renders the field for use in the Card Template
         *
         * Example: `{{type:Front}}`
         */
        @CheckResult
        abstract fun renderToTemplateTag(): String
    }

    @Parcelize
    enum class Tab(
        val position: Int,
    ) : Parcelable {
        FIELDS(0),
        SPECIAL(1),
    }

    companion object {
        const val KEY_FIELD_ITEMS = "key_field_items"
        const val KEY_INSERT_FIELD_METADATA = "key_field_options"
        const val KEY_REQUEST_KEY = "key_request_key"

        const val STATE_TAB = "state_tab"
    }
}

@Parcelize
data class InsertFieldMetadata(
    val side: SingleCardSide,
    val cardTemplateName: String,
    val noteTypeName: String,
    val tags: String?,
    val flag: Int?,
    val cardId: CardId?,
    val deck: String?,
) : Parcelable {
    val subdeck: String?
        get() = deck?.let { Decks.basename(it) }

    companion object {
        @CheckResult
        suspend fun query(
            side: SingleCardSide,
            cardTemplateName: String,
            noteTypeName: String,
            noteId: NoteId?,
            ord: Int?,
        ): InsertFieldMetadata {
            val note =
                try {
                    noteId?.let { nid -> withCol { getNote(nid) } }
                } catch (e: Exception) {
                    Timber.w(e, "failed to get note")
                    null
                }

            // BUG: This is the saved tags of the note, not the currently edited tags
            val tags =
                note
                    ?.tags
                    ?.joinToString(separator = " ")
                    // truncate, so we don't pass unbounded text into the arguments
                    ?.ellipsize(75)

            val card =
                try {
                    if (ord == null || note == null) {
                        null
                    } else {
                        // ord can be invalid if the user has in-memory template additions
                        withCol { note.cards(this).getOrNull(ord) }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "failed to get card")
                    null
                }

            return InsertFieldMetadata(
                side = side,
                cardTemplateName = cardTemplateName,
                noteTypeName = noteTypeName,
                tags = tags,
                cardId = card?.id,
                flag = card?.userFlag(),
                deck = card?.currentDeckId()?.let { did -> withCol { decks.get(did)?.name } },
            )
        }
    }
}
