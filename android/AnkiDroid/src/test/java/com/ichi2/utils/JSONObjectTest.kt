/*
 * Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.utils

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.common.utils.ext.deepClone
import com.ichi2.anki.common.utils.ext.deepClonedInto
import com.ichi2.anki.common.utils.ext.fromMap
import com.ichi2.testutils.EmptyApplication
import org.intellij.lang.annotations.Language
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * This black box test was written without inspecting the non-free org.json sourcecode.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
// TODO: move to common test
@SuppressLint("CheckResult") // many usages: checking exceptions
class JSONObjectTest {
    @Language("JSON")
    private val correctJsonBasic = """{"key1":"value1"}"""

    @Language("JSON")
    private val correctJsonNested =
        """{"key1":{"key1a":"value1a","key1b":"value1b"},"key2":"value2"}"""

    @Language("JSON")
    private val correctJsonWithArray =
        """{"key1":"value1","key2":[{"key2a":"value2a"},{"key2b":"value2b"}],"key3":"value3"}"""

    @Language("JSON")
    private val correctJsonNestedWithArray =
        """{"key1":{"key1a":"value1a","key1b":"value1b"},
            |"key2":[{"key2a":"value2a"},{"key2b":"value2b"}],"key3":"value3"}
        """.trimMargin()

    private lateinit var correctJsonObjectBasic: JSONObject
    private lateinit var correctJsonObjectNested: JSONObject
    private lateinit var correctJsonObjectWithArray: JSONObject
    private lateinit var correctJsonObjectNestedWithArray: JSONObject
    lateinit var booleanMap: MutableMap<String, Boolean>

    @Before
    @Test
    fun setUp() {
        correctJsonObjectBasic = JSONObject(correctJsonBasic)
        correctJsonObjectNested = JSONObject(correctJsonNested)
        correctJsonObjectWithArray = JSONObject(correctJsonWithArray)
        correctJsonObjectNestedWithArray = JSONObject(correctJsonNestedWithArray)
        booleanMap = HashMap()
        for (i in 0..9) {
            booleanMap["key$i"] = i % 2 == 0
        }
    }

    @Test
    fun copyJsonTest() {
        Assert.assertEquals(correctJsonObjectBasic.toString(), correctJsonObjectBasic.deepClone().toString())
        Assert.assertEquals(correctJsonObjectNested.toString(), correctJsonObjectNested.deepClone().toString())
        Assert.assertEquals(correctJsonObjectWithArray.toString(), correctJsonObjectWithArray.deepClone().toString())
    }

    private class JSONObjectSubType : JSONObject() {
        /**
         * Sample overridden function
         */
        override fun toString(): String = removeQuotes(super.toString())
    }

    @Test
    fun deepCloneTest() {
        val jsonObjectSubType = JSONObjectSubType()

        // Clone base JSONObject Type into JSONObjectSubType
        correctJsonObjectNestedWithArray.deepClonedInto(jsonObjectSubType)

        // Test by passing result of base JSONObject's toString() to removeQuotes()
        // This is already done in the JSONObjectSubType object
        Assert.assertEquals(removeQuotes(correctJsonObjectNestedWithArray.toString()), jsonObjectSubType.toString())
    }

    /**
     * Tests that the a new copy is returned instead of a reference to the original.
     */
    @Test
    fun deepCloneReferenceTest() {
        val clone = correctJsonObjectBasic.deepClone()
        // Both objects should point to different memory address
        Assert.assertNotEquals(clone, correctJsonObjectBasic)
    }

    @Test
    fun fromMapTest() {
        val fromMapJsonObject = fromMap(booleanMap)
        for (i in 0..9) {
            Assert.assertEquals(fromMapJsonObject.getBoolean("key$i"), i % 2 == 0)
        }
    }

    companion object {
        /**
         * Wraps all the alphanumeric words in a string in quotes
         */
        private fun removeQuotes(string: String): String = string.replace("\"([a-zA-Z0-9]+)\"".toRegex(), "$1")
    }
}
