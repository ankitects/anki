// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import org.intellij.lang.annotations.Language
import org.json.JSONObject
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class) // required due to differing JSON implementation
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class FieldTest : AndroidTest {
    @Test
    fun `'tag' - null handling`() {
        val jsonObject = JSONObject()
        val field = Field(jsonObject)

        assertNull(field.imageOcclusionTag, message = "{ }")

        jsonObject.put("tag", JSONObject.NULL)
        assertNull(field.imageOcclusionTag, message = "{ tag: null }")

        jsonObject.put("tag", "1")
        assertEquals("1", field.imageOcclusionTag, message = """{ tag: 1 }""")
    }

    @Test
    fun `toString is unchanged`() {
        @Language("JSON")
        val expected = """{"name":"Test"}"""

        val jsonObject = JSONObject()
        jsonObject.put("name", "Test")

        val field = Field(JSONObject())
        field.name = "Test"

        assertEquals(jsonObject.toString(), field.toString())
        assertEquals(expected, field.toString())
    }
}
