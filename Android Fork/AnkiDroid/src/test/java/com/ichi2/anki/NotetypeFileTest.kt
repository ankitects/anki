// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.testutils.EmptyApplication
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class NotetypeFileTest {
    @get:Rule
    val tempDirectory = TemporaryFolder()

    @Test
    @Config(shadows = [RejectWholeDocumentParsing::class])
    fun `preview snapshot is parsed without a whole document string`() {
        val file = tempDirectory.newFile().apply { writeText("""{"css":".card {}"}""") }

        assertEquals(".card {}", NotetypeFile(file.path).getNotetype().css)
    }

    @Test
    @Config(shadows = [RejectWholeDocumentParsing::class])
    fun `editor snapshot is parsed without a whole document string`() {
        val file = tempDirectory.newFile().apply { writeText("""{"css":".card {}"}""") }

        assertEquals(".card {}", CardTemplateNotetype.getTempNoteType(file.path).css)
    }

    @Implements(JSONObject::class)
    class RejectWholeDocumentParsing {
        @Implementation
        @Suppress("ktlint:standard:function-naming") // Robolectric's constructor hook.
        fun __constructor__(json: String): Unit = error("Snapshot reading must not parse a whole document string")
    }

    @Test
    fun `preview snapshot does not materialize the whole JSON document`() {
        val notetype = notetypeWithoutStringification()

        val file = NotetypeFile(tempDirectory.root, notetype)

        assertEquals(notetype.css, file.getNotetype().css)
    }

    @Test
    fun `editor snapshot does not materialize the whole JSON document`() {
        val notetype = notetypeWithoutStringification()
        val path =
            CardTemplateNotetype.saveTempNoteType(
                ApplicationProvider.getApplicationContext(),
                notetype,
            )

        assertNotNull(path)
        try {
            assertEquals(notetype.css, CardTemplateNotetype.getTempNoteType(path).css)
        } finally {
            File(path).delete()
        }
    }

    @Test
    fun `snapshot preserves large Unicode templates and remains independent of the source`() {
        val template = "x".repeat(8191) + "😀 中文 é\n\"\\" + "y".repeat(8191)
        val notetype =
            NotetypeJson(
                JSONObject()
                    .put("css", template)
                    .put("tmpls", JSONArray().put(JSONObject().put("qfmt", template))),
            )

        val file = NotetypeFile(tempDirectory.root, notetype)
        notetype.css = "changed after saving"
        notetype.templates[0].qfmt = "changed after saving"
        val restored = file.getNotetype()

        assertEquals(template, restored.css)
        assertEquals(template, restored.templates[0].qfmt)
    }

    private fun notetypeWithoutStringification(): NotetypeJson =
        NotetypeJson(
            object : JSONObject() {
                // Issue 21881: the temporary JSON string alone can exhaust the Android heap.
                override fun toString(): String = error("Snapshot writing must not stringify the whole note type")
            }.apply {
                put("css", ".card { color: black; }")
            },
        )
}
