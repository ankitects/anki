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

import androidx.lifecycle.SavedStateHandle
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.cardviewer.SingleCardSide.BACK
import com.ichi2.anki.cardviewer.SingleCardSide.FRONT
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel.SelectedField
import com.ichi2.anki.dialogs.InsertFieldDialogViewModel.SelectedField.NoteTypeField
import com.ichi2.anki.model.SpecialFields
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.jupiter.api.assertInstanceOf
import kotlin.test.assertNotNull

/**
 * Test for [InsertFieldDialogViewModel]
 */
class InsertFieldDialogViewModelTest {
    @Test
    fun `expected fields are exposed`() =
        withViewModel {
            assertThat(
                "Note type fields are copied",
                fieldNames.map { it.name },
                equalTo(listOf("Front", "Back")),
            )
        }

    @Test
    fun `field selection emits data`() =
        withViewModel {
            assertThat(selectedFieldFlow.value, nullValue())

            selectNamedField(fieldNames[0])

            val selectedField = assertNotNull(selectedFieldFlow.value)
            val field = assertInstanceOf<NoteTypeField>(selectedField)
            assertThat(field.renderToTemplateTag(), equalTo("{{Front}}"))
        }

    @Test
    fun `special field ordering (Front)`() =
        withViewModel(side = FRONT) {
            assertThat(
                this.specialFields,
                equalTo(
                    with(SpecialFields) {
                        listOf(
                            Deck,
                            Subdeck,
                            Tags,
                            Flag,
                            NoteType,
                            CardTemplate,
                            CardId,
                        )
                    },
                ),
            )
        }

    @Test
    fun `special field ordering (Back)`() =
        withViewModel(side = BACK) {
            assertThat(
                this.specialFields,
                equalTo(
                    with(SpecialFields) {
                        listOf(
                            FrontSide,
                            Deck,
                            Subdeck,
                            Tags,
                            Flag,
                            NoteType,
                            CardTemplate,
                            CardId,
                        )
                    },
                ),
            )
        }

    @Test
    fun `special field selection emits data`() =
        withViewModel {
            assertThat(selectedFieldFlow.value, nullValue())

            selectSpecialField(SpecialFields.Deck)

            val selectedField = assertNotNull(selectedFieldFlow.value)
            val field = assertInstanceOf<SelectedField.SpecialField>(selectedField)
            assertThat(field.renderToTemplateTag(), equalTo("{{Deck}}"))
        }

    fun withViewModel(
        fieldList: List<String> = listOf("Front", "Back"),
        side: SingleCardSide = FRONT,
        block: InsertFieldDialogViewModel.() -> Unit,
    ) {
        val savedStateHandle =
            SavedStateHandle().apply {
                this[InsertFieldDialogViewModel.KEY_FIELD_ITEMS] = ArrayList(fieldList)
                this[InsertFieldDialogViewModel.KEY_INSERT_FIELD_METADATA] =
                    InsertFieldMetadata(
                        side = side,
                        cardTemplateName = "Card Template",
                        noteTypeName = "Note Type",
                        tags = "tag1 tag2",
                        cardId = 1,
                        deck = "aa::bb",
                        flag = 0,
                    )
            }
        withViewModel(savedStateHandle, block)
    }

    fun withViewModel(
        savedStateHandle: SavedStateHandle,
        block: InsertFieldDialogViewModel.() -> Unit,
    ) {
        InsertFieldDialogViewModel(savedStateHandle).run(block)
    }
}
