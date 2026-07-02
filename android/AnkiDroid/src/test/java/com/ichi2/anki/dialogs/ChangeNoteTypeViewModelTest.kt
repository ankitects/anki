/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.ChangeNoteTypeViewModel.Companion.ARG_NOTE_IDS
import com.ichi2.anki.dialogs.ChangeNoteTypeViewModelTest.Launch.Cloze
import com.ichi2.anki.dialogs.ChangeNoteTypeViewModelTest.Launch.Custom
import com.ichi2.anki.dialogs.ChangeNoteTypeViewModelTest.Launch.Regular
import com.ichi2.anki.dialogs.SelectedIndex.NOTHING
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.NotetypeJson
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class ChangeNoteTypeViewModelTest : RobolectricTest() {
    @Test
    fun `no changes to save`() =
        // on Anki Desktop, this keeps the screen open with a 'no changes to save' alert
        viewModelTest {
            val expectedException =
                assertFailsWith<ChangeNoteTypeException> { executeChangeNoteTypeAsync().await() }
            assertThat(expectedException.message, equalTo("No changes to save"))
            assertThat(expectedException.kind.toString(targetContext), equalTo("No changes to save"))
        }

    @Test
    fun `integration test - regular`() =
        viewModelTest {
            // A basic card mapped to '"Basic (optional reversed card)"'
            val basicAndOptionalReversed = col.notetypes.byName("Basic (optional reversed card)")!!
            setOutputNoteType(basicAndOptionalReversed)

            // check fields
            assertThat("field changes", fieldChangeMap.size, equalTo(3))
            assertThat("Front -> Front", fieldChangeMap[0], equalTo(0))
            assertThat("Back -> Back", fieldChangeMap[1], equalTo(1))
            assertThat("(Nothing) -> Add Reverse", fieldChangeMap[2], equalTo(null))

            // check templates
            assertThat("template changes", templateChangeMap.size, equalTo(2))
            assertThat("Card 1 -> Card 1", templateChangeMap[0], equalTo(0))
            assertThat("(Nothing) -> Card 2", templateChangeMap[1], equalTo(null))

            // Front -> Back
            this.updateFieldMapping(1, SelectedIndex.from(0))
            this.updateTemplateMapping(1, SelectedIndex.from(0))

            this.executeChangeNoteTypeAsync().await()

            val note = col.getNote(this.noteIds.single())

            assertThat("note type is updated", note.noteTypeId, equalTo(basicAndOptionalReversed.id))
            assertThat("first field: default mapping", note.fields[0], equalTo("Front"))
            assertThat("second field: custom duplicate mapping", note.fields[1], equalTo("Front"))
            assertThat("third field: unmapped", note.fields[2], equalTo(""))
        }

    @Test
    fun `integration test - regular to cloze`() =
        viewModelTest(Regular(front = "{{c1::test}} {{c2::more}}")) {
            // A basic card mapped to '"Basic (optional reversed card)"'
            val cloze = col.notetypes.byName("Cloze")!!
            setOutputNoteType(cloze)

            // check fields
            assertThat("field changes", fieldChangeMap.size, equalTo(2))
            assertThat("Front -> Cloze", fieldChangeMap[0], equalTo(0))
            assertThat("Back -> Extra", fieldChangeMap[1], equalTo(1))

            assertThat(canMapTemplates(), equalTo(false))

            // Front -> Back
            this.updateFieldMapping(1, SelectedIndex.from(0))

            this.executeChangeNoteTypeAsync().await()

            val note = col.getNote(this.noteIds.single())

            assertThat("note type is updated", note.noteTypeId, equalTo(cloze.id))
            assertThat("first field: default mapping", note.fields[0], equalTo("{{c1::test}} {{c2::more}}"))
            assertThat("second field: custom duplicate mapping", note.fields[1], equalTo("{{c1::test}} {{c2::more}}"))
            assertThat("cards are generated", note.numberOfCards(), equalTo(2))
        }

    @Test
    fun `integration test - cloze to regular`() =
        viewModelTest(Cloze(cloze = "{{c1::Test}}")) {
            val basicAndReversed = col.notetypes.byName("Basic (and reversed card)")!!
            setOutputNoteType(basicAndReversed)

            assertThat(canMapTemplates(), equalTo(false))

            // Back = {{c1::Test}}
            this.updateFieldMapping(1, SelectedIndex.from(0))

            this.executeChangeNoteTypeAsync().await()

            val note = col.getNote(this.noteIds.single())

            assertThat("note type is updated", note.noteTypeId, equalTo(basicAndReversed.id))
            assertThat("first field: default mapping", note.fields[0], equalTo("{{c1::Test}}"))
            assertThat("second field: custom duplicate mapping", note.fields[1], equalTo("{{c1::Test}}"))
            assertThat("a new card is generated", note.numberOfCards(), equalTo(2))
        }

    @Test
    fun `integration test - cloze`() =
        viewModelTest(Cloze()) {
            val idOfClozeTwo = addClozeNoteType()
            viewModelTest(Cloze(cloze = "{{c1::test}} {{c2::more}}")) {
                setOutputNoteTypeId(idOfClozeTwo)

                // check fields
                assertThat("field changes", fieldChangeMap.size, equalTo(2))
                assertThat("Cloze -> Cloze", fieldChangeMap[0], equalTo(0))
                assertThat("Extra -> Extra", fieldChangeMap[1], equalTo(1))

                assertThat(canMapTemplates(), equalTo(false))

                // Cloze -> Extra
                this.updateFieldMapping(1, SelectedIndex.from(0))

                this.executeChangeNoteTypeAsync().await()

                val note = col.getNote(this.noteIds.single())

                assertThat("note type is updated", note.noteTypeId, equalTo(idOfClozeTwo))
                assertThat("first field: default mapping", note.fields[0], equalTo("{{c1::test}} {{c2::more}}"))
                assertThat("second field: custom duplicate mapping", note.fields[1], equalTo("{{c1::test}} {{c2::more}}"))
                assertThat("cards are retained", note.numberOfCards(), equalTo(2))
            }
        }

    @Test
    fun `can save if only id differs`() {
        val duplicateBasic = regularNoteType(arrayOf("Front", "Back"))
        viewModelTest {
            setOutputNoteType(duplicateBasic)
            executeChangeNoteTypeAsync().await()
        }
    }

    @Test
    fun `change both cards to nothing`() {
        val twoCards = col.notetypes.byName("Basic (and reversed card)")!!
        viewModelTest(Custom(twoCards)) {
            assertThat(col.noteCount(), equalTo(1))
            updateTemplateMapping(0, mappedFrom = NOTHING)
            updateTemplateMapping(1, mappedFrom = NOTHING)
            executeChangeNoteTypeAsync().await()
        }
        // TODO: what should happen here (other than no failure):
        // Anki desktop shows the cards as 'deleted', then revives them on refresh
    }

    @Test
    fun `initially the input note type is the output `() =
        viewModelTest {
            assertThat(this.inputNoteType.id, equalTo(outputNoteType.id))
        }

    @Test
    fun `nothing is an option for fields`() =
        viewModelTest {
            discardedFieldsFlow.test {
                updateFieldMapping(outputFieldIndex = 0, mappedFrom = NOTHING).join()
                assertThat(expectMostRecentItem(), equalTo(listOf("Front")))
            }
        }

    @Test
    fun `nothing is an option for templates`() =
        viewModelTest {
            discardedTemplatesFlow.test {
                updateTemplateMapping(outputTemplateIndex = 0, mappedFrom = NOTHING).join()
                assertThat(expectMostRecentItem(), equalTo(listOf("Card 1")))
            }
        }

    @Test
    fun `card template default mapping - equal number of templates`() =
        viewModelTest(Regular(templateCount = 2)) {
            assertThat(templateChangeMap.size, equalTo(2))
            assertThat(templateChangeMap[0], equalTo(0))
            assertThat(templateChangeMap[1], equalTo(1))
        }

    @Test
    fun `card template default mapping - more input templates`() =
        viewModelTest(Regular(templateCount = 3)) {
            setOutputNoteType(col.notetypes.byName("Basic")!!)

            assertThat(templateChangeMap.size, equalTo(1))
            assertThat(templateChangeMap[0], equalTo(0))
        }

    @Test
    fun `card template default mapping - more output templates`() {
        val noteType = col.notetypes.byName(addNoteTypeWithTemplateCount(3))!!
        viewModelTest {
            setOutputNoteType(noteType)

            assertThat(templateChangeMap.size, equalTo(3))
            assertThat(templateChangeMap[0], equalTo(0))
            assertThat(templateChangeMap[1], equalTo(null))
            assertThat(templateChangeMap[2], equalTo(null))
        }
    }

    @Test
    fun `field default mapping - equal number of fields`() {
        val nt = regularNoteType(arrayOf("Hello", "World"))
        viewModelTest {
            setOutputNoteType(nt)

            assertThat(fieldChangeMap.size, equalTo(2))
            assertThat(fieldChangeMap[0], equalTo(0))
            assertThat(fieldChangeMap[1], equalTo(1))
        }
    }

    @Test
    fun `field default mapping - more output fields`() {
        val nt = regularNoteType(arrayOf("Hello", "World", "Three"))
        viewModelTest {
            setOutputNoteType(nt)

            assertThat(fieldChangeMap.size, equalTo(3))
            assertThat(fieldChangeMap[0], equalTo(0))
            assertThat(fieldChangeMap[1], equalTo(1))
            assertThat(fieldChangeMap[2], equalTo(null))
        }
    }

    @Test
    fun `field default mapping - more input fields`() {
        val nt = regularNoteType(arrayOf("Hello"))
        viewModelTest {
            setOutputNoteType(nt)

            assertThat(fieldChangeMap.size, equalTo(1))
            assertThat(fieldChangeMap[0], equalTo(0))
        }
    }

    @Test
    fun `field default mapping - first name equal`() {
        val nt = regularNoteType(arrayOf("Match", "A", "A2"))
        val nt2 = regularNoteType(arrayOf("Match", "B", "B2"))
        viewModelTest(Custom(noteType = nt)) {
            setOutputNoteType(nt2)

            assertThat(fieldChangeMap.size, equalTo(3))
            assertThat(fieldChangeMap[0], equalTo(0))
            assertThat(fieldChangeMap[1], equalTo(1))
            assertThat(fieldChangeMap[2], equalTo(2))
        }
    }

    @Test
    fun `field default mapping - last name equal`() {
        val nt = regularNoteType(arrayOf("A", "A2", "Match"))
        val nt2 = regularNoteType(arrayOf("B", "B2", "Match"))
        viewModelTest(Custom(noteType = nt)) {
            setOutputNoteType(nt2)

            assertThat(fieldChangeMap.size, equalTo(3))
            assertThat(fieldChangeMap[0], equalTo(0))
            assertThat(fieldChangeMap[1], equalTo(1))
            assertThat(fieldChangeMap[2], equalTo(2))
        }
    }

    @Test
    fun `field default mapping - middle to start`() {
        val nt = regularNoteType(arrayOf("A", "Match", "A2"))
        val nt2 = regularNoteType(arrayOf("Match", "B", "B2"))
        viewModelTest(Custom(noteType = nt)) {
            setOutputNoteType(nt2)

            assertThat(fieldChangeMap.size, equalTo(3))
            assertThat("Match -> Match", fieldChangeMap[0], equalTo(1))
            assertThat("A -> B", fieldChangeMap[1], equalTo(0))
            assertThat("A2 -> B2", fieldChangeMap[2], equalTo(2))
        }
    }

    @Test
    fun `field default mapping - middle to end`() {
        val nt = regularNoteType(arrayOf("A", "Match", "A2"))
        val nt2 = regularNoteType(arrayOf("B", "B2", "Match"))
        viewModelTest(Custom(noteType = nt)) {
            setOutputNoteType(nt2)

            assertThat(fieldChangeMap.size, equalTo(3))
            assertThat("A -> B", fieldChangeMap[0], equalTo(0))
            assertThat("Match -> Match", fieldChangeMap[1], equalTo(2))
            assertThat("A2 -> B2", fieldChangeMap[2], equalTo(1))
        }
    }

    @Test
    fun `field default mapping - more output fields with name matches`() {
        val nt = regularNoteType(arrayOf("A", "Match", "A2"))
        val nt2 = regularNoteType(arrayOf("B", "B2", "Match", "B3"))
        viewModelTest(Custom(noteType = nt)) {
            setOutputNoteType(nt2)

            assertThat(fieldChangeMap.size, equalTo(4))
            assertThat("A -> B", fieldChangeMap[0], equalTo(0))
            assertThat("Match -> Match", fieldChangeMap[1], equalTo(2))
            assertThat("A2 -> B2", fieldChangeMap[2], equalTo(1))
            assertThat("B3 -> (Nothing)", fieldChangeMap[3], equalTo(null))
        }
    }

    @Test
    fun `field default mapping - more input fields with name matches`() {
        val nt = regularNoteType(arrayOf("A", "Match", "A2", "A3"))
        val nt2 = regularNoteType(arrayOf("B", "B2", "Match"))
        viewModelTest(Custom(noteType = nt)) {
            setOutputNoteType(nt2)

            assertThat(fieldChangeMap.size, equalTo(3))
            assertThat("A -> B", fieldChangeMap[0], equalTo(0))
            assertThat("Match -> Match", fieldChangeMap[1], equalTo(2))
            assertThat("A2 -> B2", fieldChangeMap[2], equalTo(1))
        }
    }

    @Test
    fun `alert on deleted card template`() =
        viewModelTest {
            discardedTemplatesFlow.test {
                assertThat(expectMostRecentItem().size, equalTo(0))
                updateTemplateMapping(outputTemplateIndex = 0, mappedFrom = NOTHING).join()
                expectMostRecentItem().also {
                    assertThat(it.size, equalTo(1))
                    assertThat(it.single(), equalTo("Card 1"))
                }
            }
        }

    @Test
    fun `a field can be missing without an explicit nothing mapping`() =
        viewModelTest {
            discardedFieldsFlow.test {
                assertThat(expectMostRecentItem().size, equalTo(0))

                // [Front] on the new note is mapped to from [Front] in the old card
                updateFieldMapping(outputFieldIndex = 0, mappedFrom = SelectedIndex.from(0)).join()
                // [Back] on the new note is mapped to from [Front] in the old card
                updateFieldMapping(outputFieldIndex = 1, mappedFrom = SelectedIndex.from(0)).join()

                // => [Back] in the old card is unmapped, even though no fields map to nothing
                expectMostRecentItem().also {
                    assertThat(it.size, equalTo(1))
                    assertThat(it.single(), equalTo("Back"))
                }
            }
        }

    @Test
    fun `a card template can't be mapped twice`() =
        viewModelTest(Regular(templateCount = 2)) {
            assertThat(templateMappingOf("Card 1"), equalTo(SelectedIndex.from(0)))
            updateCardMapping(from = "Card 2", to = "Card 1")
            assertThat(templateMappingOf("Card 1"), equalTo(NOTHING))
        }

    @Test
    fun `templates can only be set in regular to regular`() {
        val cloze = col.notetypes.byName("Cloze")!!
        val basic = col.notetypes.byName("Basic")!!

        viewModelTest(Regular(templateCount = 2)) {
            assertThat("can change regular -> regular", canMapTemplates(), equalTo(true))
            this.setOutputNoteType(cloze)
            assertThat("can't change templates if regular -> cloze", canMapTemplates(), equalTo(false))
        }

        viewModelTest(Cloze()) {
            assertThat("can't change from cloze -> cloze", canMapTemplates(), equalTo(false))
            this.setOutputNoteType(basic)
            assertThat("can't change from cloze -> regular", canMapTemplates(), equalTo(false))
        }
    }

    @Test
    fun `init fails if no notes`() {
        val ex = assertFailsWith<IllegalArgumentException> { buildViewModel(noteIds = emptyList()) }
        assertThat(ex.message, equalTo("ARG_NOTE_IDS was empty"))
    }

    @Test
    fun `init fails if notes are not distinct`() {
        val ex = assertFailsWith<IllegalArgumentException> { buildViewModel(noteIds = listOf(1, 1)) }
        assertThat(ex.message, equalTo("ARG_NOTE_IDS was not distinct"))
    }

    @Test
    fun `selecting a new note type resets fields`() =
        viewModelTest {
            updateFieldMapping(1, mappedFrom = SelectedIndex.from(0))
            assertThat(fieldChangeMap[1], equalTo(0))

            // select a different note type, then return
            setOutputNoteType(col.notetypes.byName("Cloze")!!)
            setOutputNoteType(inputNoteType)

            assertThat("Field changes should be reset", fieldChangeMap[1], not(equalTo(0)))
        }

    @Test
    fun `selecting a new note type resets templates`() =
        viewModelTest {
            updateTemplateMapping(1, mappedFrom = SelectedIndex.from(0))
            assertThat(templateChangeMap[1], equalTo(0))

            // select a different note type, then return
            setOutputNoteType(col.notetypes.byName("Cloze")!!)
            setOutputNoteType(inputNoteType)

            assertThat("Template changes should be reset", templateChangeMap[1], not(equalTo(0)))
        }

    @Test
    fun `meta test - templateCount`() =
        viewModelTest(Regular(templateCount = 5)) {
            assertThat(this.inputNoteType.templates.size, equalTo(5))
        }

    private fun viewModelTest(
        noteTypeInput: Launch = Regular(),
        block: suspend ChangeNoteTypeViewModel.() -> Unit,
    ) = runTest {
        val noteIds = noteTypeInput.setup()
        block(
            buildViewModel(noteIds = noteIds),
        )
    }

    private fun buildViewModel(noteIds: List<NoteId>) =
        ChangeNoteTypeViewModel(
            SavedStateHandle(
                mapOf(ARG_NOTE_IDS to noteIds.toLongArray()),
            ),
        )

    fun Launch.setup(): List<NoteId> =
        when (this) {
            is Regular -> {
                when (templateCount) {
                    1 ->
                        addNotes(
                            count = this.noteCount,
                            front = this.front ?: "Front",
                        ).map { it.id }
                    else -> {
                        val noteType =
                            addNoteTypeWithTemplateCount(
                                templateCount = templateCount,
                                front = this.front ?: "Front",
                            )
                        List(noteCount) {
                            addNoteUsingNoteTypeName(noteType, *arrayOf("Hello", "World"))
                        }.map { it.id }
                    }
                }
            }
            is Cloze -> {
                List(this.noteCount) { addClozeNote(this.cloze) }.map { it.id }
            }
            is Custom -> {
                List(noteCount) {
                    // 1, 2, 3 etc...
                    val fieldValues = Array(noteType.fields.size) { (it + 1).toString() }
                    addNoteUsingNoteTypeName(noteType.name, *fieldValues)
                }.map { it.id }
            }
        }

    private var regularNoteTypeCounter: Int = 1

    private fun regularNoteType(fields: Array<String>): NotetypeJson {
        val nt = addStandardNoteType("Standard ${regularNoteTypeCounter++}", fields = fields, "{{Hello}}", "{{Hello}}")
        return col.notetypes.byName(nt)!!
    }

    private fun addNoteTypeWithTemplateCount(
        templateCount: Int,
        front: String = "Front",
    ) = addStandardNoteType("$templateCount Templates", arrayOf(front, "Back"), "{{Front}}", "{{Back}}", templateCount = templateCount)

    sealed class Launch {
        data class Regular(
            val noteCount: Int = 1,
            val templateCount: Int = 1,
            val front: String? = null,
        ) : Launch()

        data class Cloze(
            val noteCount: Int = 1,
            val cloze: String = "{{c1::Hi}}",
        ) : Launch()

        data class Custom(
            val noteType: NotetypeJson,
            val noteCount: Int = 1,
        ) : Launch()
    }
}

suspend fun ChangeNoteTypeViewModel.updateCardMapping(
    from: String,
    to: String,
) = updateTemplateMapping(
    outputTemplateIndex = this.inputNoteType.templatesNames.indexOf(from),
    mappedFrom = this.templateNameToIndex(to),
).join()

private fun ChangeNoteTypeViewModel.templateMappingOf(from: String): SelectedIndex {
    val index = this.inputNoteType.templatesNames.indexOf(from)
    return templateChangeMap[index].let {
        if (it == null) NOTHING else SelectedIndex.from(it)
    }
}

private suspend fun ChangeNoteTypeViewModel.setOutputNoteType(noteType: NotetypeJson) = setOutputNoteTypeId(noteType.id).await()

private fun ChangeNoteTypeViewModel.templateNameToIndex(name: String) =
    SelectedIndex.Index(this.outputNoteType.templatesNames.indexOf(name))

private fun ChangeNoteTypeViewModel.canMapTemplates(): Boolean = canChangeTemplatesFlow.value
