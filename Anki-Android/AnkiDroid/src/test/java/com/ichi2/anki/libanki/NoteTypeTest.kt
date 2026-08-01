/*
 *  Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
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
package com.ichi2.anki.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.notetypes.copy
import com.ichi2.anki.backend.stripHTML
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.libanki.exception.ConfirmModSchemaException
import com.ichi2.anki.libanki.testutils.clozeClass
import com.ichi2.anki.libanki.testutils.ext.addNote
import com.ichi2.anki.libanki.testutils.ext.newNote
import com.ichi2.testutils.JvmTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@KotlinCleanup("improve kotlin code where possible")
class NoteTypeTest : JvmTest() {
    @Test
    fun test_frontSide_field() {
        // #8951 - Anki Special-cases {{FrontSide}} on the front to return empty string
        val noteType = col.notetypes.current()
        noteType.templates[0].qfmt = "{{Front}}{{FrontSide}}"
        col.notetypes.save(noteType)
        val note = col.newNote()
        note.setItem("Front", "helloworld")
        col.addNote(note)
        val card = note.firstCard()
        val q = card.question()
        assertThat(
            "field should be at the end of the template - empty string for front",
            q,
            endsWith("helloworld"),
        )
        assertThat(
            "field should not have a problem",
            q,
            not(containsString("has a problem")),
        )
    }

    @Test
    fun test_field_named_frontSide() {
        // #8951 - A field named "FrontSide" is ignored - this matches Anki 2.1.34 (8af8f565)
        val noteType = col.notetypes.current()

        // Add a field called FrontSide and FrontSide2 (to ensure that fields are added correctly)
        col.notetypes.addFieldModChanged(noteType, col.notetypes.newField("FrontSide"))
        col.notetypes.addFieldModChanged(noteType, col.notetypes.newField("FrontSide2"))
        noteType.templates[0].qfmt = "{{Front}}{{FrontSide}}{{FrontSide2}}"
        col.notetypes.save(noteType)

        val note = col.newNote()
        note.setItem("Front", "helloworld")
        note.setItem("FrontSide", "1")
        note.setItem("FrontSide2", "2")
        col.addNote(note)
        val card = note.firstCard()
        val q = card.question()
        assertThat(
            "FrontSide should be an empty string, even though it was set",
            q,
            endsWith("helloworld2"),
        )
    }

    /*****************
     * Models       *
     */
    @Test
    @Throws(ConfirmModSchemaException::class)
    fun test_modelDelete() {
        val note = col.newNote()
        note.setItem("Front", "1")
        note.setItem("Back", "2")
        col.addNote(note)
        assertEquals(1, col.cardCount())
        col.notetypes.remove(col.notetypes.current().id)
        assertEquals(0, col.cardCount())
    }

    @Test
    fun test_modelCopy() {
        val noteType = col.notetypes.current()
        val noteType2 = col.notetypes.copy(noteType)
        assertEquals("Basic copy", noteType2.name)
        assertNotEquals(noteType2.id, noteType.id)
        assertEquals(2, noteType2.fields.length())
        assertEquals(2, noteType.fields.length())
        assertEquals(noteType.fields.length(), noteType2.fields.length())
        assertEquals(1, noteType.templates.length())
        assertEquals(1, noteType2.templates.length())
        assertEquals(col.notetypes.scmhash(noteType), col.notetypes.scmhash(noteType2))
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun test_fields() {
        var note = col.newNote()
        note.setItem("Front", "1")
        note.setItem("Back", "2")
        col.addNote(note)
        val noteType = col.notetypes.current()
        // make sure renaming a field updates the templates
        col.notetypes.renameFieldLegacy(noteType, noteType.fields[0], "NewFront")
        assertThat(noteType.templates[0].qfmt, containsString("{{NewFront}}"))
        val h = col.notetypes.scmhash(noteType)
        // add a field
        var field: Field? = col.notetypes.newField("foo")
        col.notetypes.addFieldLegacy(noteType, field!!)
        assertEquals(
            listOf("1", "2", ""),
            col
                .getNote(
                    col.notetypes.nids(
                        noteType,
                    )[0],
                ).fields,
        )
        assertNotEquals(h, col.notetypes.scmhash(noteType))
        // rename it
        field = noteType.fields[2]
        col.notetypes.renameFieldLegacy(noteType, field, "bar")
        assertEquals("", col.getNote(col.notetypes.nids(noteType)[0]).getItem("bar"))
        // delete back
        col.notetypes.remFieldLegacy(noteType, noteType.fields[1])
        assertEquals(
            listOf("1", ""),
            col
                .getNote(
                    col.notetypes.nids(
                        noteType,
                    )[0],
                ).fields,
        )
        // move 0 -> 1
        col.notetypes.moveFieldLegacy(noteType, noteType.fields[0], 1)
        assertEquals(
            listOf("", "1"),
            col
                .getNote(
                    col.notetypes.nids(
                        noteType,
                    )[0],
                ).fields,
        )
        // move 1 -> 0
        col.notetypes.moveFieldLegacy(noteType, noteType.fields[1], 0)
        assertEquals(
            listOf("1", ""),
            col
                .getNote(
                    col.notetypes.nids(
                        noteType,
                    )[0],
                ).fields,
        )
        // add another and put in middle
        field = col.notetypes.newField("baz")
        col.notetypes.addFieldLegacy(noteType, field)
        note = col.getNote(col.notetypes.nids(noteType)[0])
        note.setItem("baz", "2")
        note.flush()
        assertEquals(
            listOf("1", "", "2"),
            col
                .getNote(
                    col.notetypes.nids(
                        noteType,
                    )[0],
                ).fields,
        )
        // move 2 -> 1
        col.notetypes.moveFieldLegacy(noteType, noteType.fields[2], 1)
        assertEquals(
            listOf("1", "2", ""),
            col
                .getNote(
                    col.notetypes.nids(
                        noteType,
                    )[0],
                ).fields,
        )
        // move 0 -> 2
        col.notetypes.moveFieldLegacy(noteType, noteType.fields[0], 2)
        assertEquals(
            listOf("2", "", "1"),
            col
                .getNote(
                    col.notetypes.nids(
                        noteType,
                    )[0],
                ).fields,
        )
        // move 0 -> 1
        col.notetypes.moveFieldLegacy(noteType, noteType.fields[0], 1)
        assertEquals(
            listOf("", "2", "1"),
            col
                .getNote(
                    col.notetypes.nids(
                        noteType,
                    )[0],
                ).fields,
        )
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun test_templates() {
        val noteType = col.notetypes.current()
        var t =
            Notetypes.newTemplate("Reverse").apply {
                qfmt = "{{Back}}"
                afmt = "{{Front}}"
            }
        col.notetypes.addTemplateModChanged(noteType, t)
        col.notetypes.save(noteType)
        val note = col.newNote()
        note.setItem("Front", "1")
        note.setItem("Back", "2")
        col.addNote(note)
        assertEquals(2, col.cardCount())
        val cards: List<Card> = note.cards()
        assertEquals(2, cards.size)
        var c = cards[0]
        val c2 = cards[1]
        // first card should have first ord
        assertEquals(0, c.ord)
        assertEquals(1, c2.ord)
        // switch templates
        col.notetypes.repositionTemplate(noteType, c.template(), 1)
        col.notetypes.save(noteType)
        c.load()
        c2.load()
        assertEquals(1, c.ord)
        assertEquals(0, c2.ord)
        // removing a template should delete its cards
        col.notetypes.removeTemplate(noteType, noteType.templates[0])
        col.notetypes.save(noteType)
        assertEquals(1, col.cardCount())
        // and should have updated the other cards' ordinals
        c = note.cards()[0]
        assertEquals(0, c.ord)
        assertEquals("1", stripHTML(c.question()))
        // it shouldn't be possible to orphan notes by removing templates
        t =
            Notetypes.newTemplate("template name").apply {
                qfmt = "{{Front}}1"
            }
        col.notetypes.addTemplateModChanged(noteType, t)
        col.notetypes.removeTemplate(noteType, noteType.templates[0])
        col.notetypes.save(noteType)
        assertEquals(
            0,
            col.db.queryLongScalar(
                "select count() from cards where nid not in (select id from notes)",
            ),
        )
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    fun test_cloze_ordinals() {
        col.notetypes.setCurrent(col.notetypes.byName("Cloze")!!)
        val noteType = col.notetypes.current()

        // We replace the default Cloze template
        val t =
            Notetypes.newTemplate("ChainedCloze").apply {
                qfmt = "{{text:cloze:Text}}"
                afmt = "{{text:cloze:Text}}"
            }
        col.notetypes.addTemplateModChanged(noteType, t)
        col.notetypes.save(noteType)
        col.notetypes.removeTemplate(noteType, noteType.templates[0])
        col.notetypes.save(noteType)

        val note = col.newNote()
        note.setItem("Text", "{{c1::firstQ::firstA}}{{c2::secondQ::secondA}}")
        col.addNote(note)
        assertEquals(2, col.cardCount())
        val cards: List<Card> = note.cards()
        assertEquals(2, cards.size)
        val c = cards[0]
        val c2 = cards[1]
        // first card should have first ord
        assertEquals(0, c.ord)
        assertEquals(1, c2.ord)
    }

    @Test
    fun test_text() {
        val noteType =
            col.notetypes.current().apply {
                templates[0].qfmt = "{{text:Front}}"
            }
        col.notetypes.save(noteType)
        val note = col.newNote()
        note.setItem("Front", "hello<b>world")
        col.addNote(note)
        assertThat(note.cards()[0].question(), containsString("helloworld"))
    }

    @Test
    fun test_cloze() {
        fun clearId(note: Note) {
            // backend protects against adding the same note twice
            note.id = 0
        }
        col.notetypes.setCurrent(col.notetypes.byName("Cloze")!!)
        var note = col.newNote()
        assertEquals("Cloze", note.notetype.name)
        // a cloze model with no clozes is not empty
        note.setItem("Text", "nothing")
        assertEquals(1, col.addNote(note))
        clearId(note)
        assertEquals(1, col.addNote(note))
        clearId(note)
        assertEquals(1, col.addNote(note))
        // try with one cloze
        note = col.newNote()
        note.setItem("Text", "hello {{c1::world}}")
        assertEquals(1, col.addNote(note))
        clearId(note)
        assertEquals(1, col.addNote(note))
        clearId(note)
        assertEquals(1, col.addNote(note))
    }

    @Test
    fun test_cloze_mathjax() {
        col.notetypes.setCurrent(col.notetypes.byName("Cloze")!!)
        var note = col.newNote()
        note.setItem(
            "Text",
            "{{c1::ok}} \\(2^2\\) {{c2::not ok}} \\(2^{{c3::2}}\\) \\(x^3\\) {{c4::blah}} {{c5::text with \\(x^2\\) jax}}",
        )
        assertNotEquals(0, col.addNote(note))
        assertEquals(5, note.numberOfCards())
        assertThat(note.cards()[0].question(), containsString(clozeClass()))
        assertThat(note.cards()[1].question(), containsString(clozeClass()))
        assertThat(
            note.cards()[2].question(),
            not(containsString(clozeClass())),
        )
        assertThat(note.cards()[3].question(), containsString(clozeClass()))
        assertThat(note.cards()[4].question(), containsString(clozeClass()))

        note = col.newNote()
        note.setItem("Text", "\\(a\\) {{c1::b}} \\[ {{c1::c}} \\]")
        assertNotEquals(0, col.addNote(note))
        assertEquals(1, note.numberOfCards())
    }

    @Test
    fun test_type_and_cloze() {
        val noteType = col.notetypes.byName("Cloze")
        col.notetypes.setCurrent(noteType!!)
        noteType.templates[0].qfmt = "{{cloze:Text}}{{type:cloze:Text}}"
        col.notetypes.save(noteType)
        val note = col.newNote()
        note.setItem("Text", "hello {{c1::world}}")
        col.addNote(note)
        assertThat(
            note.cards()[0].question(),
            containsString("[[type:cloze:Text]]"),
        )
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    @Suppress("SpellCheckingInspection") // chaine
    fun test_chained_mods() {
        col.notetypes.setCurrent(col.notetypes.byName("Cloze")!!)
        val noteType = col.notetypes.current()

        // We replace the default Cloze template
        val t =
            Notetypes.newTemplate("ChainedCloze").apply {
                qfmt = "{{cloze:text:Text}}"
                afmt = "{{cloze:text:Text}}"
            }
        col.notetypes.addTemplateModChanged(noteType, t)
        col.notetypes.save(noteType)
        col.notetypes.removeTemplate(noteType, noteType.templates[0])
        col.notetypes.save(noteType)
        val note = col.newNote()
        val q1 = "<span style=\"color:red\">phrase</span>"
        val a1 = "<b>sentence</b>"
        val q2 = "<span style=\"color:red\">en chaine</span>"
        val a2 = "<i>chained</i>"
        note.setItem("Text", "This {{c1::$q1::$a1}} demonstrates {{c1::$q2::$a2}} clozes.")
        assertEquals(1, col.addNote(note))
        note.cards()[0].question()
        /* TODO: chained modifier
        assertThat("Question «"+question+"» does not contain the expected string", question, containsString("This <span class=cloze>[sentence]</span> demonstrates <span class=cloze>[chained]</span> clozes.")
                   );
        assertThat(note.cards().get(0).a(), containsString("This <span class=cloze>phrase</span> demonstrates <span class=cloze>en chaine</span> clozes."
                                                    ));

         */
    }

    @Test
    @Throws(ConfirmModSchemaException::class)
    @Suppress("deprecation") // notetypes.change
    fun test_modelChange() {
        val cloze = col.notetypes.byName("Cloze")
        // enable second template and add a note
        val basic = col.notetypes.current()
        val t =
            Notetypes.newTemplate("Reverse").apply {
                qfmt = "{{Back}}"
                afmt = "{{Front}}"
            }
        col.notetypes.addTemplateModChanged(basic, t)
        col.notetypes.save(basic)
        var note = col.newNote()
        note.setItem("Front", "note")
        note.setItem("Back", "b123")
        col.addNote(note)
        // switch fields
        var map: MutableMap<Int, Int?> = HashMap()
        val noOp = mapOf<Int, Int?>(0 to 0, 1 to 1)
        map[0] = 1
        map[1] = 0
        col.notetypes.change(basic, note.id, basic, map, noOp)
        note.load()
        assertEquals("b123", note.getItem("Front"))
        assertEquals("note", note.getItem("Back"))
        // switch cards
        val c0 = note.cards()[0]
        val c1 = note.cards()[1]
        assertThat(c0.question(), containsString("b123"))
        assertThat(c1.question(), containsString("note"))
        assertEquals(0, c0.ord)
        assertEquals(1, c1.ord)
        col.notetypes.change(basic, note.id, basic, noOp, map)
        note.load()
        c0.load()
        c1.load()
        assertThat(c0.question(), containsString("note"))
        assertThat(c1.question(), containsString("b123"))
        assertEquals(1, c0.ord)
        assertEquals(0, c1.ord)
        // .cards() returns cards in order
        assertEquals(c1.id, note.cards()[0].id)
        // delete first card
        map = HashMap()
        map[0] = null
        map[1] = 1
        col.notetypes.change(basic, note.id, basic, noOp, map)
        note.load()
        c0.load()
        // the card was deleted
        // but we have two cards, as a new one was generated
        assertEquals(2, note.numberOfCards())
        // an unmapped field becomes blank
        assertEquals("b123", note.getItem("Front"))
        assertEquals("note", note.getItem("Back"))
        col.notetypes.change(basic, note.id, basic, map, noOp)
        note.load()
        assertEquals("", note.getItem("Front"))
        assertEquals("note", note.getItem("Back"))
        // another note to try model conversion
        note = col.newNote()
        note.setItem("Front", "f2")
        note.setItem("Back", "b2")
        col.addNote(note)
        // counts = col.getModels().all_use_counts();
        // Using older version of the test
        assertEquals(2, col.notetypes.useCount(basic))
        assertEquals(0, col.notetypes.useCount(cloze!!))
        // Identity map
        map = HashMap()
        map[0] = 0
        map[1] = 1
        col.notetypes.change(basic, note.id, cloze, map, map)
        note.load()
        assertEquals("f2", note.getItem("Text"))
        assertEquals(2, note.numberOfCards())
        // back the other way, with deletion of second ord
        col.notetypes.removeTemplate(basic, basic.templates[1])
        col.notetypes.save(basic)
        assertEquals(
            2,
            col.db.queryScalar("select count() from cards where nid = ?", note.id),
        )
        map = HashMap()
        map[0] = 0
        col.notetypes.change(cloze, note.id, basic, map, map)
        assertEquals(
            1,
            col.db.queryScalar("select count() from cards where nid = ?", note.id),
        )
    }

    @Test
    fun nonEmptyFieldTest() {
        val basic = col.notetypes.byName("Basic")
        val s: MutableSet<String> = HashSet<String>()
        assertEquals(s, basic!!.nonEmptyFields(arrayOf("", "")))
        s.add("Front")
        assertEquals(
            s,
            basic.nonEmptyFields(arrayOf("<br/>", "   \t ")),
        ) // Html is not stripped to check for card generation
        assertEquals(s, basic.nonEmptyFields(arrayOf("P", "")))
        s.add("Back")
        assertEquals(s, basic.nonEmptyFields(arrayOf("P", "A")))
    }

    /**
     * tests if Model.getDid() returns model did
     * or default deck id (1) if null
     */

    @Test
    fun getDid_test() {
        val basic = col.notetypes.byName("Basic")
        basic!!.did = 999L

        val expected = 999L
        assertEquals("getDid() should return the model did", expected, basic.did)

        // Check if returns default deck id (1) when did is null
        basic.jsonObject.put("did", null as Int?)
        val expected2 = 1L
        assertEquals(
            "getDid() should return 1 (default deck id) if model did is null",
            expected2,
            basic.did,
        )
    }

    @Test
    fun test_updateNotetype_clears_cache() {
        val noteType = col.notetypes.current()
        val originalName = noteType.name
        val noteTypeId = noteType.id

        val cached1 = col.notetypes.get(noteTypeId)!!
        assertEquals("Cached notetype should have original name", originalName, cached1.name)

        val renamedNotetype =
            col.getNotetype(noteTypeId).copy {
                this.name = "${originalName}_renamed"
            }
        col.updateNotetype(renamedNotetype)

        val cached2 = col.notetypes.get(noteTypeId)!!
        assertEquals("Cached notetype should be cleared and show new name", "${originalName}_renamed", cached2.name)

        val allNotetypes = col.notetypes.all()
        val foundNotetype = allNotetypes.find { it.id == noteTypeId }!!
        assertEquals("Notetype in all() should have new name", "${originalName}_renamed", foundNotetype.name)
    }
}
