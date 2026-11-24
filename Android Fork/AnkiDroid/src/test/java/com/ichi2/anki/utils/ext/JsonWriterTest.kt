// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import android.util.JsonWriter
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.EmptyApplication
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.StringWriter
import java.io.Writer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class JsonWriterTest {
    @Test
    fun `JSON values round trip through streaming serialization`() {
        val text = "Quotes: \" \\ /\n\r\t\b\u000c\u0000\u001f\u2028\u2029 é 中文 😀"
        val source =
            JSONObject().apply {
                put("escaped key: \"\n", text)
                put("int", Int.MIN_VALUE)
                put("long", Long.MAX_VALUE)
                put("decimal", 1.25)
                put("true", true)
                put("false", false)
                put("null", JSONObject.NULL)
                put("emptyObject", JSONObject())
                put("emptyArray", JSONArray())
                put(
                    "nested",
                    JSONArray()
                        .put(JSONObject().put("text", text))
                        .put(JSONArray().put(false).put(42).put(JSONObject.NULL))
                        .put(4, "after empty slot"),
                )
            }
        val output = StringWriter()

        JsonWriter(output).use { it.writeObject(source) }

        val restored = JSONObject(output.toString())
        assertEquals(source.toString(), restored.toString())
        assertEquals(Long.MAX_VALUE, restored.getLong("long"))
        assertEquals(text, restored.getString("escaped key: \"\n"))
    }

    @Test
    fun `large JSON is written in bounded chunks`() {
        val template = "x".repeat(32 * 1024)
        val source = JSONObject().put("templates", JSONArray().apply { repeat(32) { put(template) } })
        var written = 0L
        val sink =
            object : Writer() {
                override fun write(
                    buffer: CharArray,
                    offset: Int,
                    length: Int,
                ) {
                    assertTrue(length <= 8192, "Unexpected write of $length characters")
                    written += length
                }

                override fun flush() = Unit

                override fun close() = Unit
            }

        JsonWriter(sink.buffered()).use { it.writeObject(source) }

        assertEquals("{\"templates\":[]}".length + 32L * (template.length + 2) + 31, written)
    }

    @Test
    fun `write failures propagate to the caller`() {
        val failure = IOException("Disk full")
        val sink =
            object : Writer() {
                override fun write(
                    buffer: CharArray,
                    offset: Int,
                    length: Int,
                ): Unit = throw failure

                override fun flush() = Unit

                override fun close() = Unit
            }

        val thrown =
            assertFailsWith<IOException> {
                JsonWriter(sink).writeObject(JSONObject().put("css", ".card {}"))
            }

        assertSame(failure, thrown)
    }
}
