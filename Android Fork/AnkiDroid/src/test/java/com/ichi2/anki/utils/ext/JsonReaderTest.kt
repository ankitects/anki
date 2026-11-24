// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import android.util.JsonReader
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.EmptyApplication
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.Reader
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class JsonReaderTest {
    @get:Rule
    val tempDirectory = TemporaryFolder()

    @Test
    fun `streaming reads preserve JSON values and large escaped Unicode strings`() {
        val text = "x".repeat(8191) + "😀 中文 é\n\"\\\u0000\u2028" + "y".repeat(8191)
        val source =
            JSONObject()
                .put("escaped key: \"\n", text)
                .put("integer", Int.MIN_VALUE)
                .put("id", Long.MAX_VALUE)
                .put("negative", Long.MIN_VALUE)
                .put("fraction", 1.25)
                .put("true", true)
                .put("false", false)
                .put("null", JSONObject.NULL)
                .put("emptyObject", JSONObject())
                .put("emptyArray", JSONArray())
                .put("nested", JSONArray().put(JSONObject().put("text", text)).put(JSONArray().put(JSONObject.NULL)))
        val file = tempDirectory.newFile()
        file.writeJson(source)

        val restored = file.readJson()

        assertEquals(source.toString(), restored.toString())
        assertEquals(Long.MAX_VALUE, restored.getLong("id"))
        assertEquals(Long.MIN_VALUE, restored.getLong("negative"))
        assertEquals(text, restored.getString("escaped key: \"\n"))
    }

    @Test
    fun `numeric tokens retain integer precision and accept exponent notation`() {
        val file =
            tempDirectory.newFile().apply {
                writeText("""{"id":9007199254740993,"exponent":1e3,"negativeExponent":1e-3,"negativeZero":-0.0}""")
            }

        val restored = file.readJson()

        assertEquals(9007199254740993L, restored.getLong("id"))
        assertEquals(1000.0, restored.getDouble("exponent"))
        assertEquals(0.001, restored.getDouble("negativeExponent"))
        assertEquals((-0.0).toBits(), restored.getDouble("negativeZero").toBits())
    }

    @Test
    fun `truncated invalid and trailing JSON is rejected as an IO failure`() {
        for (json in listOf("", "[]", "{", "{\"value\":", "{\"value\":1e999}", "{} {}", "{} trailing")) {
            val file = tempDirectory.newFile().apply { writeText(json) }
            assertFailsWith<IOException>(json) { file.readJson() }
        }
    }

    @Test
    fun `read failures propagate to the caller`() {
        val failure = IOException("Unable to read snapshot")
        val source =
            object : Reader() {
                override fun read(
                    buffer: CharArray,
                    offset: Int,
                    length: Int,
                ): Int = throw failure

                override fun close() = Unit
            }

        val thrown =
            assertFailsWith<IOException> {
                JsonReader(source).use { it.readObject() }
            }

        assertSame(failure, thrown)
    }
}
