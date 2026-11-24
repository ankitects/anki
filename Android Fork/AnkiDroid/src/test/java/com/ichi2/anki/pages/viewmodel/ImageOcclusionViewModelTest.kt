// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.anki.pages.viewmodel.ImageOcclusionViewModel.Companion.ARG_IMAGE_OCCLUSION
import com.ichi2.anki.pages.viewmodel.NoteIdBuilder.Id
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.isJsonEqual
import com.ichi2.testutils.withViewModel
import kotlinx.coroutines.flow.first
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.assertNull
import kotlin.properties.Delegates.notNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests [ImageOcclusionViewModel]
 */
class ImageOcclusionViewModelTest : JvmTest() {
    private var deckIdToSwitchTo by notNull<DeckId>()

    @Test
    fun `IDs beyond JavaScript number precision are serialized as decimal strings`() {
        val id = 9007199254740993L
        assertEquals(id.toString(), ImageOcclusionArgs.Edit(id).toImageOcclusionMode().get("noteId"))
        assertEquals(id.toString(), ImageOcclusionArgs.Add("/image.png", id, 1).toImageOcclusionMode().get("notetypeId"))
    }

    @Before
    override fun setUp() {
        super.setUp()
        deckIdToSwitchTo = addDeck(OTHER_DECK_NAME)
    }

    @Test
    fun `ADD - deck name is set`() =
        withAddViewModel {
            assertThat("deckId", selectedDeckIdFlow?.value, equalTo(1))
            assertNotNull(deckNameFlow)
            assertThat("deck name", deckNameFlow.first(), equalTo("Default"))
        }

    @Test
    fun `ADD - deck selection changes id`() =
        withAddViewModel {
            val deckIdFlow = assertNotNull(selectedDeckIdFlow, "selectedDeckIdFlow")
            deckIdFlow.test {
                assertThat("initial deck id", awaitItem(), equalTo(1))
                handleDeckSelection(deckIdToSwitchTo)
                assertThat("changed deck id", awaitItem(), equalTo(deckIdToSwitchTo))
            }
        }

    @Test
    fun `ADD - deck selection changes name`() =
        withAddViewModel {
            val deckNameFlow = assertNotNull(deckNameFlow, "deckNameFlow")
            deckNameFlow.test {
                assertThat("initial deck name", awaitItem(), equalTo("Default"))
                handleDeckSelection(deckIdToSwitchTo)
                assertThat("changed deck name", awaitItem(), equalTo(OTHER_DECK_NAME))
            }
        }

    // This test is subject to change
    //  *  decks.current could only be set during the 'add' operation
    //  *  the proto could be extended to accept a deckId
    @Test
    fun `ADD - selected deck is changed and reverted`() =
        withAddViewModel {
            // change the deck
            assertNotNull(deckNameFlow)
            deckNameFlow.test {
                assertThat("initial deck", this.awaitItem(), equalTo("Default"))
                assertThat("initial current deck", col.decks.getCurrentId(), equalTo(1))

                handleDeckSelection(deckIdToSwitchTo)

                assertThat("changed deck", this.awaitItem(), equalTo(OTHER_DECK_NAME))
                assertThat("changed current deck", col.decks.getCurrentId(), equalTo(deckIdToSwitchTo))
            }

            onSaveOperationCompleted()
            assertThat("current deck is reverted", col.decks.getCurrentId(), equalTo(1))
        }

    @Test
    fun `EDIT - flows are null`() =
        withEditViewModel {
            assertNull(selectedDeckIdFlow)
            assertNull(deckNameFlow)
        }

    @Test
    fun `EDIT - full test`() =
        withEditViewModel {
            // effectively a no-op: call doesn't do anything for 'EDIT' yet
            assertDoesNotThrow { onSaveOperationCompleted() }
        }

    @Test
    fun `ADD - args are copied correctly`() =
        withAddViewModel(imagePath = "/", noteTypeId = 12, deckId = 2) {
            val args = assertInstanceOf<ImageOcclusionArgs.Add>(args)

            val expected =
                ImageOcclusionArgs.Add(
                    imagePath = "/",
                    noteTypeId = 12,
                    originalDeckId = 2,
                )
            assertThat(args, equalTo(expected))
        }

    @Test
    fun `EDIT - args are copied correctly`() =
        withEditViewModel(noteIdBuilder = Id(12)) {
            val args = assertInstanceOf<ImageOcclusionArgs.Edit>(args)
            val expected = ImageOcclusionArgs.Edit(noteId = 12)
            assertThat(args, equalTo(expected))
        }

    @Test
    fun `ADD - JSON Serialization`() {
        val expected = """
            {
                "kind": "add",
                "imagePath":  "/",
                "notetypeId": "12"
            }
        """

        withAddViewModel(imagePath = "/", noteTypeId = 12, deckId = 2) {
            assertThat(args.toImageOcclusionMode(), isJsonEqual(expected))
        }
    }

    @Test
    fun `EDIT - JSON Serialization`() {
        val expected = """
            {
                "kind": "edit",
                "noteId": "12"
            }
        """

        withEditViewModel(noteIdBuilder = Id(12)) {
            assertThat(args.toImageOcclusionMode(), isJsonEqual(expected))
        }
    }

    companion object {
        const val OTHER_DECK_NAME = "Temp"
    }
}

fun AnkiTest.withViewModel(
    args: ImageOcclusionArgs,
    block: suspend ImageOcclusionViewModel.() -> Unit,
) = runTest {
    val savedStateHandle =
        SavedStateHandle().apply {
            this[ARG_IMAGE_OCCLUSION] = args
        }

    withViewModel(ImageOcclusionViewModel(savedStateHandle), block)
}

fun AnkiTest.withAddViewModel(
    imagePath: String = "",
    noteTypeId: NoteTypeId = 0,
    deckId: DeckId = Consts.DEFAULT_DECK_ID,
    block: suspend ImageOcclusionViewModel.() -> Unit,
) = withViewModel(
    ImageOcclusionArgs.Add(
        imagePath = imagePath,
        noteTypeId = noteTypeId,
        originalDeckId = deckId,
    ),
    block,
)

fun AnkiTest.withEditViewModel(
    noteIdBuilder: NoteIdBuilder = NoteIdBuilder.CreateNew,
    block: suspend ImageOcclusionViewModel.() -> Unit,
) = withViewModel(
    ImageOcclusionArgs.Edit(
        noteId = noteIdBuilder.build(this),
    ),
    block,
)

sealed class NoteIdBuilder {
    companion object CreateNew : NoteIdBuilder()

    data class Id(
        val id: NoteId,
    ) : NoteIdBuilder()

    fun build(testContext: AnkiTest): NoteId =
        when (this) {
            is Id -> id
            is CreateNew -> testContext.addBasicNote().id
        }
}
