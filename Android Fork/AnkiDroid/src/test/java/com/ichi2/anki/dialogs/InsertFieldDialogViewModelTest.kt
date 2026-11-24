// SPDX-License-Identifier: GPL-3.0-or-later

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
                this[InsertFieldDialogViewModel.ARG_FIELD_NAMES] = ArrayList(fieldList)
                this[InsertFieldDialogViewModel.ARG_INSERT_FIELD_METADATA] =
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
