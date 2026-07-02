/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.jupiter.api.assertThrows

class ConfigTest : InMemoryAnkiTest() {
    @Test
    fun string_serialization() {
        assertThat(col.config.get<String>("sortType"), equalTo("noteFld"))
        col.config.set("sortType", "noteFld2")
        assertThat(col.config.get<String>("sortType"), equalTo("noteFld2"))
        col.config.set("bb", JSONObject.NULL)
        assertThat(col.config.get<String>("bb"), equalTo(null))
        col.config.set("cc", "null")
        assertThat(col.config.get<String>("cc"), equalTo("null"))
    }

    @Test
    fun getOpt() {
        col.config.set("int", 5)
        assertThat(col.config.get("int"), equalTo(5))
        // explicitly nulled key should work
        col.config.set("null", JSONObject.NULL)
        val b: Int? = null
        assertThat(col.config.get("null"), equalTo(b))
        // missing key should be the same
        assertThat(col.config.get("missing"), equalTo(b))
        // type mismatch should also be null
        col.config.set("float", 5.5)
        assertThat(col.config.get("float"), equalTo(b))
        // other types
        col.config.set("str", "hello")
        assertThat(col.config.get("str"), equalTo("hello"))
        col.config.set("list", listOf(1, 2, 3))
        assertThat(col.config.get("list"), equalTo(listOf(1, 2, 3)))
        val obj = Example("foo", 5)
        col.config.set("example", obj)
        assertThat(col.config.get("example"), equalTo(obj))
        val map = mapOf("one" to 1, "two" to 2)
        col.config.set("map", map)
        assertThat(col.config.get("map"), equalTo(map))
        val map2 = mapOf("one" to 1, "two" to "two")
        assertThrows<SerializationException> {
            // heterogenerous maps are not supported
            col.config.set("map2", map2)
        }
    }

    @Test
    fun `A string value is handled as a long - Issue 14096`() {
        col.config.set("test", '"' + "1688546411954" + '"')
        col.config.set("test1", "1688546411954")
        col.config.set("test2", 1688546411954)

        assertThat(col.config.get<Long>("test"), equalTo(null))
        assertThat(col.config.get<Long>("test1"), equalTo(1688546411954L))
        assertThat(col.config.get<Long>("test2"), equalTo(1688546411954L))
    }
}

@Serializable
data class Example(
    val hello: String,
    val world: Int,
)
