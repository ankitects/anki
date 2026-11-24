// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.common.utils.ext.contentEquals
import com.ichi2.anki.common.utils.ext.getStringOrNull
import com.ichi2.anki.common.utils.ext.jsonObjectIterable
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.intellij.lang.annotations.Language
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class) // This is necessary, android and JVM differ on JSONObject.NULL
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class JSONObjectTest : AndroidTest {
    @Test
    fun `contentEquals compares nested values independently of object key order`() {
        val first = JSONObject("""{"css":"é 😀","values":[true,false,null,{},[],{"front":"a","back":"b"}]}""")
        val second = JSONObject("""{"values":[true,false,null,{},[],{"back":"b","front":"a"}],"css":"é 😀"}""")

        assertTrue(first.contentEquals(first))
        assertTrue(first.contentEquals(second))
        assertTrue(second.contentEquals(first))
    }

    @Test
    fun `contentEquals detects changed keys types and nested values`() {
        val differentValues =
            listOf(
                "{}" to """{"value":null}""",
                """{"first":null}""" to """{"second":null}""",
                """{"value":null}""" to """{"value":"null"}""",
                """{"value":1}""" to """{"value":"1"}""",
                """{"value":true}""" to """{"value":false}""",
                """{"value":{}}""" to """{"value":[]}""",
                """{"values":[1,2]}""" to """{"values":[2,1]}""",
                """{"values":[1]}""" to """{"values":[1,2]}""",
                """{"values":[{"front":"a"}]}""" to """{"values":[{"front":"b"}]}""",
            )

        for ((first, second) in differentValues) {
            assertFalse(JSONObject(first).contentEquals(JSONObject(second)), "$first and $second")
            assertFalse(JSONObject(second).contentEquals(JSONObject(first)), "$second and $first")
        }
    }

    @Test
    fun `contentEquals compares numbers as JSON without losing long precision`() {
        fun number(value: Number) = JSONObject().put("value", value)

        assertTrue(number(1).contentEquals(number(1L)))
        assertTrue(number(1L).contentEquals(number(1.0)))
        assertFalse(number(-0.0).contentEquals(number(0)))
        assertFalse(number(9007199254740992L).contentEquals(number(9007199254740993L)))
        assertFalse(number(Long.MAX_VALUE).contentEquals(number(Long.MAX_VALUE - 1)))
    }

    @Test
    fun `contentEquals treats empty array slots as JSON null`() {
        val sparse = JSONObject().put("values", JSONArray().put(2, "third"))
        val explicit = JSONObject().put("values", JSONArray().put(JSONObject.NULL).put(JSONObject.NULL).put("third"))

        assertTrue(sparse.contentEquals(explicit))
        assertTrue(explicit.contentEquals(sparse))
    }

    @Test
    fun `test getStringOrNull`() {
        fun test(value: Any) = JSONObject().apply { put("test", value) }.getStringOrNull("test")

        assertNull(JSONObject().getStringOrNull("test"), message = "{}")
        assertNull(test(JSONObject.NULL), message = "{ test: null }")
        // WARN: this differs between pure JVM and Robolectric/Android
        // On Robolectric/Android, this is {}.
        // On JVM following using the standard implementation it's null.
        assertNotNull(test(JSONObject()), message = "test: { }")
        assertNotNull(test("null"), message = """{ test: "null" }""")
        assertNotNull(test("1"), message = """{ test: "1" }""")
    }

    @Test
    fun `test jsonObjectIterable`() {
        fun jsonObjectIterable(
            @Language("JSON") json: String,
        ) = JSONObject(json).jsonObjectIterable().toList()

        assertThat(jsonObjectIterable("{}"), empty())

        with(jsonObjectIterable("""{"1": {"name":  "hello"}}""")) {
            assertThat(this, hasSize(1))
            assertThat(this[0].getString("name"), equalTo("hello"))
        }

        assertFailsWith<JSONException> { jsonObjectIterable("") }
    }
}
