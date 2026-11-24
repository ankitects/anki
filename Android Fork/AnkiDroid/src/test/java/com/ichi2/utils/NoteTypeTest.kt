// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.utils

import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.utils.ext.getAllClozeTextFields
import junit.framework.TestCase.assertEquals
import org.json.JSONObject
import kotlin.test.Test

// link to a method in `NoteType.kt` for navigation as it contains no classes

/** Test of [NoteType][templates] */
class NoteTypeTest {
    private val noteType =
        JSONObject(
            """
        {
          "type":1,
          "tmpls":[
               {
                 "name":"Cloze",
                 "ord":0,
                 "qfmt":"{{type:cloze:Text}} {{type:cloze:Text2}} {{cloze:Text3}} {{Added field}}",
                 "afmt":"{{cloze:Text}}<br>\n{{Back Extra}}",
                 "bqfmt":"",
                 "bafmt":"",
                 "did":null,
                 "bfont":"",
                 "bsize":0,
                 "id":1716321740
              }
           ]
        }
    """,
        )

    @Test
    fun testQfmtField() {
        val notetypeJson = NotetypeJson(noteType)

        val expectedQfmt = "{{type:cloze:Text}} {{type:cloze:Text2}} {{cloze:Text3}} {{Added field}}"
        assertEquals(expectedQfmt, notetypeJson.templates[0].qfmt)
    }

    @Test
    fun testGetAllClozeTexts() {
        val notetypeJson = NotetypeJson(noteType)

        val expectedClozeTexts = listOf("Text", "Text2", "Text3")
        assertEquals(expectedClozeTexts, notetypeJson.getAllClozeTextFields())
    }

    @Test
    fun testNameField() {
        val notetypeJson = NotetypeJson(noteType)
        val expectedName = "Cloze"
        assertEquals(expectedName, notetypeJson.templates[0].name)
    }

    @Test
    fun testOrdField() {
        val notetypeJson = NotetypeJson(noteType)
        val expectedOrd = 0
        assertEquals(expectedOrd, notetypeJson.templates[0].ord)
    }

    @Test
    fun testAfmtField() {
        val notetypeJson = NotetypeJson(noteType)
        val expectedAfmt = "{{cloze:Text}}<br>\n{{Back Extra}}"
        assertEquals(expectedAfmt, notetypeJson.templates[0].afmt)
    }
}
