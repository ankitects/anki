// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.fragment.app.commitNow
import androidx.fragment.app.testing.launchFragment
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.dialogs.InsertFieldDialog.Companion.RESULT_INSERTED_FIELD
import com.ichi2.anki.model.FieldName
import com.ichi2.anki.model.SpecialField
import com.ichi2.anki.model.SpecialFields
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.emptyString
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import com.ichi2.anki.common.android.R as CommonR

/** Tests for [InsertFieldDialog] */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class InsertFieldDialogTest : RobolectricTest() {
    val metadata =
        InsertFieldMetadata(
            side = SingleCardSide.FRONT,
            cardTemplateName = "A",
            noteTypeName = "B",
            tags = "tag1 tag2",
            cardId = 1,
            deck = "aa::bb",
            flag = 0,
        )

    @Test
    fun `selection waits for the dialog view to be recreated`() =
        withDialog { dialog, results ->
            val manager = dialog.parentFragmentManager
            val viewModel = ViewModelProvider(dialog)[InsertFieldDialogViewModel::class.java]
            manager.commitNow { detach(dialog) }
            assertNull(dialog.view)

            viewModel.selectNamedField(FieldName("Front"))
            advanceRobolectricLooper()
            assertEquals(emptyList(), results, "A destroyed view must not deliver the selection")

            manager.commitNow { attach(dialog) }
            advanceRobolectricLooper()
            assertEquals(listOf("{{Front}}"), results)
            assertFalse(dialog.isAdded, "Selecting a field dismisses the dialog")
        }

    @Test
    fun `recreated dialog delivers a special field selection once`() =
        withDialog { dialog, results ->
            val manager = dialog.parentFragmentManager
            manager.commitNow { detach(dialog) }
            manager.commitNow { attach(dialog) }
            advanceRobolectricLooper()

            ViewModelProvider(dialog)[InsertFieldDialogViewModel::class.java].selectSpecialField(SpecialFields.Tags)
            advanceRobolectricLooper()

            assertEquals(listOf("{{Tags}}"), results)
            assertFalse(dialog.isAdded, "Selecting a field dismisses the dialog")
        }

    private fun withDialog(block: (InsertFieldDialog, MutableList<String>) -> Unit) {
        val requestKey = "insert_field"
        launchFragment<InsertFieldDialog>(
            fragmentArgs = InsertFieldDialog.newInstance(listOf("Front", "Back"), metadata, requestKey).requireArguments(),
            themeResId = CommonR.style.Theme_AnkiDroid,
        ).use { scenario ->
            scenario.onFragment { dialog ->
                val results = mutableListOf<String>()
                dialog.parentFragmentManager.setFragmentResultListener(requestKey, dialog.requireActivity()) { _, result ->
                    results.add(requireNotNull(result.getString(RESULT_INSERTED_FIELD)))
                }
                block(dialog, results)
            }
        }
    }

    @Test
    fun `all special fields have a descriptions`() {
        val allSpecialFields = SpecialFields.ALL

        for (field in allSpecialFields) {
            assertThat(field.buildDescription(), not(emptyString()))
        }
    }

    @Test
    fun `{{Type}} description uses note type name`() {
        val metadata = metadata.copy(noteTypeName = "A")

        assertThat(
            SpecialFields.NoteType.buildDescription(metadata = metadata),
            equalTo("The name of the note type: ‘A’"),
        )
    }

    @Test
    fun `{{Card}} description uses card template name`() {
        val metadata = metadata.copy(cardTemplateName = "B")

        assertThat(
            SpecialFields.CardTemplate.buildDescription(metadata = metadata),
            equalTo(
                "The name of the card template: ‘B’",
            ),
        )
    }

    @Test
    fun `{{CardFlag}} description with missing flag`() {
        assertThat(
            SpecialFields.Flag.buildDescription(
                metadata.copy(flag = null),
            ),
            equalTo("Outputs ‘flagN’, where N is the flag code (0–7)"),
        )
    }

    @Test
    fun `{{CardFlag}} description with flag`() {
        assertThat(
            SpecialFields.Flag.buildDescription(
                metadata.copy(flag = 3),
            ),
            equalTo("Outputs ‘flag3’, where 3 is the flag code (0–7)"),
        )
    }

    @Test
    @Config(qualifiers = "ar")
    fun `{{CardFlag}} description in arabic`() {
        // this was previously outputting '(0–7)' in Eastern Arabic numerals
        assertThat(
            SpecialFields.Flag.buildDescription(
                metadata.copy(flag = 3),
            ),
            containsString("(0–7)"),
        )
    }

    @Test
    fun `{{Tags}} description uses tags if set`() {
        val metadata = metadata.copy(tags = "one two")
        assertThat(
            SpecialFields.Tags.buildDescription(metadata),
            equalTo("The tags of the note: ‘one two’"),
        )
    }

    @Test
    fun `{{Tags}} description if tags is blank`() {
        val metadata = metadata.copy(tags = " ")
        assertThat(
            SpecialFields.Tags.buildDescription(metadata),
            equalTo("The tags of the note"),
        )
    }

    @Test
    fun `{{Tags}} description if tags is null`() {
        val metadata = metadata.copy(tags = null)
        assertThat(
            SpecialFields.Tags.buildDescription(metadata),
            equalTo(
                """
                The tags of the note
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `{{CardID}} description if ID null`() {
        val metadata = metadata.copy(cardId = null)
        assertThat(
            SpecialFields.CardId.buildDescription(metadata),
            equalTo("The ID of the card"),
        )
    }

    @Test
    fun `{{CardID}} description if ID is set`() {
        val metadata = metadata.copy(cardId = 1767778189)
        assertThat(
            SpecialFields.CardId.buildDescription(metadata),
            equalTo("The ID of the card: ‘1767778189’"),
        )
    }

    @Test
    fun `{{Deck}} description if not set`() {
        val metadata = metadata.copy(deck = null)
        assertThat(
            SpecialFields.Deck.buildDescription(metadata),
            equalTo("The full deck of the card, including parent decks"),
        )
    }

    @Test
    fun `{{Deck}} description if set`() {
        val metadata = metadata.copy(deck = "aa::bb")
        assertThat(
            SpecialFields.Deck.buildDescription(metadata),
            equalTo("The full deck of the card, including parent decks: ‘aa::bb’"),
        )
    }

    @Test
    fun `{{Subdeck}} description if not set`() {
        val metadata = metadata.copy(deck = null)
        assertThat(
            SpecialFields.Subdeck.buildDescription(metadata),
            equalTo("The current deck of the card, excluding parent decks"),
        )
    }

    @Test
    fun `{{Subdeck}} description if set`() {
        val metadata = metadata.copy(deck = "aa::bb")
        assertThat(
            SpecialFields.Subdeck.buildDescription(metadata),
            equalTo("The current deck of the card, excluding parent decks: ‘bb’"),
        )
    }
}

context(testContext: InsertFieldDialogTest)
fun SpecialField.buildDescription(metadata: InsertFieldMetadata = testContext.metadata) =
    buildDescription(testContext.targetContext, metadata).toString()
