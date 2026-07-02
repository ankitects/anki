/*
 Copyright (c) 2021 Tarek Mohamed Abdalla <tarekkma@gmail.com>
 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.
 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.
 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.utils

import com.ichi2.utils.ListUtil.Companion.assertListEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Enclosed::class)
class TagsUtilTest {
    @RunWith(Parameterized::class)
    class GetUpdatedTagsTest {
        @Parameterized.Parameter(0)
        @JvmField // required for Parameter
        var previous: List<String>? = null

        @Parameterized.Parameter(1)
        @JvmField // required for Parameter
        var selected: List<String>? = null

        @Parameterized.Parameter(2)
        @JvmField // required for Parameter
        var indeterminate: List<String>? = null

        @Parameterized.Parameter(3)
        @JvmField // required for Parameter
        var updated: List<String>? = null

        @Test
        fun test() {
            val actual =
                TagsUtil.getUpdatedTags(
                    previous!!,
                    selected!!,
                    indeterminate!!,
                )
            assertListEquals(updated, actual)
        }

        companion object {
            // suppressed to have a symmetry in all parameters, listOf(...) should be all you need.
            @JvmStatic // required for Parameters
            @Parameterized.Parameters
            fun data(): Collection<Array<Any>> =
                listOf(
                    arrayOf(
                        listOf<Any>(),
                        listOf<Any>(),
                        listOf<Any>(),
                        listOf<Any>(),
                    ),
                    arrayOf(
                        listOf("a"),
                        listOf("b", "c"),
                        listOf<Any>(),
                        listOf("b", "c"),
                    ),
                    arrayOf(
                        listOf("a"),
                        listOf("a", "b"),
                        listOf("c"),
                        listOf("a", "b"),
                    ),
                    arrayOf(
                        listOf("a"),
                        listOf("a", "b"),
                        listOf("c"),
                        listOf("a", "b"),
                    ),
                    arrayOf(
                        listOf("a", "b", "c"),
                        listOf("a"),
                        listOf("b"),
                        listOf("a", "b"),
                    ),
                )
        }
    }

    class HierarchyTagUtilsTest {
        @Test
        fun test_getTagParts() {
            assertEquals(listOf("single"), TagsUtil.getTagParts("single"))
            assertEquals(listOf(":single:"), TagsUtil.getTagParts(":single:"))
            assertEquals(listOf("first", "second"), TagsUtil.getTagParts("first::second"))
            assertEquals(listOf("first", "second:"), TagsUtil.getTagParts("first::second:"))
            assertEquals(
                listOf("first", "second", "blank"),
                TagsUtil.getTagParts("first::second::"),
            )
            assertEquals(
                listOf("blank", "first", "blank", ":second", "blank"),
                TagsUtil.getTagParts("::first:::::second::"),
            )
        }

        @Test
        fun test_getUniformedTag() {
            assertEquals("abc", TagsUtil.getUniformedTag("abc"))
            assertEquals("Should remove trailing '::'", "abc", TagsUtil.getUniformedTag("abc::"))
            assertEquals(
                "Should replace empty immediate parts to 'blank'",
                "abc::def::blank",
                TagsUtil.getUniformedTag("abc::def::::"),
            )
        }

        @Test
        fun test_getTagRoot() {
            assertEquals("abc", TagsUtil.getTagRoot("abc"))
            assertEquals("abc:", TagsUtil.getTagRoot("abc:"))
            assertEquals("abc", TagsUtil.getTagRoot("abc::def::ghi"))
            assertEquals("blank", TagsUtil.getTagRoot("::careless"))
        }

        @Test
        fun test_getTagAncestors() {
            assertEquals(
                listOf("foo", "foo::bar", "foo::bar::aaa"),
                TagsUtil.getTagAncestors("foo::bar::aaa::bbb"),
            )
            assertEquals(listOf("foo", "foo::blank"), TagsUtil.getTagAncestors("foo::::aaa"))
            assertEquals(listOf("blank", "blank::foo"), TagsUtil.getTagAncestors("::foo::aaa"))
        }

        @Test
        fun test_compareTag() {
            assertTrue(TagsUtil.compareTag("aaa::foo", "bbb::bar") < 0)
            assertTrue(TagsUtil.compareTag("aaa::foo", "aaa::bar") > 0)
            assertTrue(TagsUtil.compareTag("aaa::foo", "aaa::foo::trailing") < 0)
            assertTrue(TagsUtil.compareTag("aaa::bbbb", "aaa::bbb::trailing") > 0)
            assertTrue(TagsUtil.compareTag("aaa::bbbz::foo", "aaa::bbb::bar") > 0)
            assertTrue(TagsUtil.compareTag("aaa::bbb9::foo", "aaa::bbb::bar") > 0)
            assertEquals(0, TagsUtil.compareTag("aaa::bbb", "aaa::bbb").toLong())
            assertEquals(0, TagsUtil.compareTag("aAa::bbb", "aaa::bBb").toLong())
        }
    }
}
