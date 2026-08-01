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
package com.ichi2.anki.dialogs.tags

import com.ichi2.testutils.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.util.Collections

class TagsListTest {
    lateinit var tagsList: TagsList
    lateinit var tagsListWithIndeterminate: TagsList

    @Before
    @Throws(Exception::class)
    fun setUp() {
        tagsList = TagsList(TAGS, CHECKED_TAGS)
        tagsListWithIndeterminate = TagsList(TAGS, CHECKED_TAGS, UNCHECKED_TAGS)
    }

    @Test
    fun test_constructor_will_remove_dups() {
        val allTags = listOf("a", "b", "a")
        val checkedTags = listOf("b", "b", "b")
        val list =
            TagsList(
                allTags,
                checkedTags,
            )

        assertEquals(
            "All tags list should not contain any duplicates",
            listOf("a", "b"),
            list.copyOfAllTagList(),
        )
        assertEquals(
            "Checked tags list should not contain any duplicates",
            listOf("b"),
            list.copyOfCheckedTagList(),
        )
    }

    @Test
    fun test_constructor_will_remove_dups_unchecked() {
        val allTags = listOf("a", "b", "a", "c", "c", "d")
        val checkedTags = listOf("b", "b", "b")
        val uncheckedTags = listOf("c", "c", "d")
        val list =
            TagsList(
                allTags,
                checkedTags,
                uncheckedTags,
            )

        assertEquals(
            "All tags list should not contain any duplicates",
            listOf("a", "b", "c", "d"),
            list.copyOfAllTagList(),
        )
        assertEquals(
            "Checked tags list should not contain any duplicates",
            listOf("b"),
            list.copyOfCheckedTagList(),
        )
        assertEquals(
            "indeterminate tags list should be empty",
            listOf<Any>(),
            list.copyOfIndeterminateTagList(),
        )
    }

    @Test
    fun test_constructor_will_ignore_casing() {
        val allTags = listOf("aA", "bb", "aa")
        val checkedTags = listOf("bb", "Bb", "bB")
        val list =
            TagsList(
                allTags,
                checkedTags,
            )

        assertEquals(
            "All tags list should not contain any duplicates (case insensitive)",
            listOf("aA", "bb"),
            list.copyOfAllTagList(),
        )
        assertEquals(
            "Checked tags list should not contain any duplicates  (case insensitive)",
            listOf("bb"),
            list.copyOfCheckedTagList(),
        )
    }

    @Test
    fun test_constructor_will_ignore_casing_unchecked() {
        val allTags = listOf("aA", "bb", "aa", "cc", "dd")
        val checkedTags = listOf("bb", "Bb", "bB", "dd", "ff")
        val uncheckedTags = listOf("BB", "cC", "cC", "dD", "CC")
        val list =
            TagsList(
                allTags,
                checkedTags,
                uncheckedTags,
            )

        assertEquals(
            "All tags list should not contain any duplicates (case insensitive)",
            listOf("aA", "bb", "cc", "dd", "ff"),
            list.copyOfAllTagList(),
        )
        assertEquals(
            "Checked tags list should not contain any duplicates  (case insensitive)",
            listOf("ff"),
            list.copyOfCheckedTagList(),
        )
        assertEquals(
            "Checked tags list should not contain any duplicates  (case insensitive)\n" +
                "and IndeterminateTagList is correct".trimIndent(),
            listOf("bb", "dd"),
            list.copyOfIndeterminateTagList(),
        )
    }

    @Test
    fun test_constructor_will_add_checked_to_all() {
        val allTags = listOf("aA", "bb", "aa")
        val checkedTags = listOf("bb", "Bb", "bB", "cc")
        val list =
            TagsList(
                allTags,
                checkedTags,
            )

        assertEquals(
            "Extra tags in checked not found in all tags, must be added to all tags list",
            listOf("aA", "bb", "cc"),
            list.copyOfAllTagList(),
        )
        assertEquals(
            "Extra tags in checked not found in all tags, must be found when retrieving checked tag list",
            listOf("bb", "cc"),
            list.copyOfCheckedTagList(),
        )
    }

    @Test
    fun test_constructor_will_add_checked_and_unchecked_to_all() {
        val allTags = listOf("aA", "bb", "aa")
        val checkedTags = listOf("bb", "Bb", "bB", "Cc", "zz")
        val uncheckedTags = listOf("BB", "cC", "cC", "dD", "CC")
        val list =
            TagsList(
                allTags,
                checkedTags,
                uncheckedTags,
            )

        assertEquals(
            "Extra tags in checked not found in all tags, must be added to all tags list",
            listOf("aA", "bb", "Cc", "zz", "dD"),
            list.copyOfAllTagList(),
        )
        assertEquals(
            "Extra tags in checked not found in all tags, must be found when retrieving checked tag list",
            listOf("zz"),
            list.copyOfCheckedTagList(),
        )
        assertEquals(listOf("bb", "Cc"), list.copyOfIndeterminateTagList())
    }

    @Test
    fun test_constructor_will_complete_hierarchy_for_all_tags() {
        val allTags = listOf("cat1", "cat2::aa", "cat3::aa::bb::cc::dd")
        val checkedTags = listOf("cat1::aa", "cat1::bb", "cat2::bb::aa", "cat2::bb::bb")
        val list =
            TagsList(
                allTags,
                checkedTags,
            )
        list.sort()
        assertEquals(
            listOf(
                "cat1",
                "cat1::aa",
                "cat1::bb",
                "cat2",
                "cat2::aa",
                "cat2::bb",
                "cat2::bb::aa",
                "cat2::bb::bb",
                "cat3",
                "cat3::aa",
                "cat3::aa::bb",
                "cat3::aa::bb::cc",
                "cat3::aa::bb::cc::dd",
            ),
            list.copyOfAllTagList(),
        )
        assertEquals(listOf("cat1::aa", "cat1::bb", "cat2::bb::aa", "cat2::bb::bb"), list.copyOfCheckedTagList())
        assertEquals(
            "Ancestors of checked tags should be marked as indeterminate",
            listOf("cat1", "cat2", "cat2::bb"),
            list.copyOfIndeterminateTagList(),
        )
    }

    @Test
    fun test_isChecked_index() {
        assertTrue("Tag at index 0 should be checked", tagsList.isChecked(0))
        assertTrue("Tag at index 3 should be checked", tagsList.isChecked(3))
        assertFalse("Tag at index 1 should be unchecked", tagsList.isChecked(1))
        assertFalse("Tag at index 6 should be unchecked", tagsList.isChecked(6))

        // indeterminate tags
        assertFalse("Tag at index 0 should be unchecked", tagsListWithIndeterminate.isChecked(0))
        assertFalse("Tag at index 3 should be unchecked", tagsListWithIndeterminate.isChecked(3))
    }

    @Test
    fun test_isChecked_object() {
        assertTrue("'programming' tag should be checked", tagsList.isChecked("programming"))
        assertTrue("'faces' tag should be checked", tagsList.isChecked("faces"))
        assertFalse("'cars' tag should be unchecked", tagsList.isChecked("cars"))
        assertFalse("'flags' tag should be unchecked", tagsList.isChecked("flags"))

        // indeterminate tags
        assertFalse("Tag at index 'programming' should be unchecked", tagsListWithIndeterminate.isChecked("programming"))
        assertFalse("Tag at index 'faces' should be unchecked", tagsListWithIndeterminate.isChecked("faces"))
    }

    @Test
    fun test_isIndeterminate_index() {
        assertFalse("Tag at index 0 should be checked (not indeterminate)", tagsList.isIndeterminate(0))
        assertFalse("Tag at index 3 should be checked (not indeterminate)", tagsList.isIndeterminate(3))
        assertFalse("Tag at index 1 should be unchecked (not indeterminate)", tagsList.isIndeterminate(1))
        assertFalse("Tag at index 6 should be unchecked (not indeterminate)", tagsList.isIndeterminate(6))

        assertTrue("Tag at index 0 should be indeterminate", tagsListWithIndeterminate.isIndeterminate(0))
        assertTrue("Tag at index 3 should be indeterminate", tagsListWithIndeterminate.isIndeterminate(3))
        assertFalse("Tag at index 1 should be unchecked (not indeterminate)", tagsListWithIndeterminate.isIndeterminate(1))
        assertFalse("Tag at index 6 should be unchecked (not indeterminate)", tagsListWithIndeterminate.isIndeterminate(6))
        assertFalse("Tag at index 6 should be unchecked (not indeterminate)", tagsListWithIndeterminate.isIndeterminate(5))
    }

    @Test
    fun test_isIndeterminate_object() {
        assertFalse("'programming' tag should be checked (not indeterminate)", tagsList.isIndeterminate("programming"))
        assertFalse("'faces' tag should be checked (not indeterminate)", tagsList.isIndeterminate("faces"))
        assertFalse("'cars' tag should be unchecked (not indeterminate)", tagsList.isIndeterminate("cars"))
        assertFalse("'flags' tag should be unchecked (not indeterminate)", tagsList.isIndeterminate("flags"))

        assertTrue("Tag 'programming' should be indeterminate", tagsListWithIndeterminate.isIndeterminate("programming"))
        assertTrue("Tag 'faces' should be indeterminate", tagsListWithIndeterminate.isIndeterminate("faces"))
        assertFalse("Tag 'cars' should be unchecked (not indeterminate)", tagsListWithIndeterminate.isIndeterminate("cars"))
        assertFalse("Tag 'flags' should be unchecked (not indeterminate)", tagsListWithIndeterminate.isIndeterminate("flags"))
    }

    @Test
    fun test_add() {
        assertTrue(
            "Adding 'anki' tag should return true, as the 'anki' is a new tag",
            tagsList.add("anki"),
        )
        assertFalse(
            "Adding 'colors' tag should return false, as the 'colors' is a already existing tag",
            tagsList.add("colors"),
        )
        assertEquals(
            "The newly added 'anki' tag should be found when retrieving all tags list",
            join(TAGS, "anki"),
            tagsList.copyOfAllTagList(),
        )
        assertSameElementsIgnoreOrder(
            "Adding operations should have nothing to do with the checked status of tags",
            CHECKED_TAGS,
            tagsList.copyOfCheckedTagList(),
        )
    }

    @Test
    fun test_add_hierarchy_tag() {
        assertTrue(
            "Adding 'language::english' tag should return true",
            tagsList.add("language::english"),
        )
        assertTrue(
            "Adding 'language::other::java' tag should return true",
            tagsList.add("language::other::java"),
        )
        assertTrue(
            "Adding 'language::other::kotlin' tag should return true",
            tagsList.add("language::other::kotlin"),
        )
        assertFalse(
            "Repeatedly adding 'language::english' tag should return false",
            tagsList.add("language::english"),
        )
        assertFalse(
            "Adding 'language::other' tag should return false, for it should have been auto created.",
            tagsList.add("language::other"),
        )
        assertTrue(tagsList.check("language::other::java"))
        assertTrue(
            "Intermediate tags should marked as indeterminate",
            tagsList.copyOfIndeterminateTagList().contains("language::other"),
        )
        assertTrue(tagsList.add("object::electronic"))
        assertTrue(tagsList.check("object::electronic"))
        assertTrue(tagsList.add("object::electronic::computer"))
        assertTrue(tagsList.check("object::electronic::computer"))
        assertFalse(
            "Should not mark checked intermediate tags as indeterminate",
            tagsList.copyOfIndeterminateTagList().contains("object::electronic"),
        )
    }

    @Test
    fun test_check() {
        assertFalse(
            "Attempting to check tag 'anki' should return false, as 'anki' is not found in all tags list",
            tagsList.check("anki"),
        ) // not in the list
        assertFalse(
            "Attempting to check tag 'colors' should return false, as 'colors' is already checked",
            tagsList.check("colors"),
        ) // already checked
        assertTrue(
            "Attempting to check tag 'flags' should return true, as 'flags' is found in all tags and is not already checked",
            tagsList.check("flags"),
        )
        assertEquals(
            "Changing the status of tags to be checked should have noting to do with all tag list",
            TAGS,
            tagsList.copyOfAllTagList(),
        ) // no change
        assertSameElementsIgnoreOrder(
            "The checked 'flags' tag should be found when retrieving list of checked tag",
            join(CHECKED_TAGS, "flags"),
            tagsList.copyOfCheckedTagList(),
        )
    }

    @Test
    fun test_check_with_indeterminate_tags_list() {
        assertTrue(
            "Attempting to check tag 'faces' should return true, as 'faces' is found in all tags and it have indeterminate state",
            tagsListWithIndeterminate.check("faces"),
        )
        assertEquals(
            "Changing the status of tags to be checked should have noting to do with all tag list",
            TAGS,
            tagsListWithIndeterminate.copyOfAllTagList(),
        )
        assertTrue(
            "The checked 'faces' tag should be found when retrieving list of checked tag",
            tagsListWithIndeterminate.copyOfCheckedTagList().contains("faces"),
        )
        assertFalse(
            "The checked 'faces' tag should not be found when retrieving list of indeterminate tags",
            tagsListWithIndeterminate.copyOfIndeterminateTagList().contains("faces"),
        )
    }

    @Test
    fun test_uncheck() {
        assertFalse(
            "Attempting to uncheck tag 'anki' should return false, as 'anki' is not found in all tags list",
            tagsList.uncheck("anki"),
        ) // not in the list
        assertFalse(
            "Attempting to uncheck tag 'flags' should return false, as 'flags' is already unchecked",
            tagsList.uncheck("flags"),
        ) // already unchecked
        assertTrue(
            "Attempting to uncheck tag 'colors' should return true, as 'colors' is found in all tags and is checked",
            tagsList.uncheck("colors"),
        )
        assertEquals(
            "Changing the status of tags to be unchecked should have noting to do with all tag list",
            TAGS,
            tagsList.copyOfAllTagList(),
        ) // no change
        assertSameElementsIgnoreOrder(
            "The unchecked 'colors' tag should be not be found when retrieving list of checked tag",
            minus(CHECKED_TAGS, "colors"),
            tagsList.copyOfCheckedTagList(),
        )
    }

    @Test
    fun test_uncheck_indeterminate_tags_list() {
        assertTrue(
            "Attempting to uncheck tag 'programming' should return true, as 'programming' " +
                "is found in all tags and it have indeterminate state",
            tagsListWithIndeterminate.uncheck("programming"),
        )
        assertEquals(
            "Changing the status of tags to be checked should have noting to do with all tag list",
            TAGS,
            tagsListWithIndeterminate.copyOfAllTagList(),
        )
        assertFalse(
            "Changing from indeterminate to unchecked should not affect checked tags",
            tagsListWithIndeterminate.copyOfCheckedTagList().contains("programming"),
        )
        assertFalse(
            "The checked 'programming' tag should not be found when retrieving list of indeterminate tags",
            tagsListWithIndeterminate.copyOfIndeterminateTagList().contains("programming"),
        )
    }

    @Test
    fun test_toggleAllCheckedStatuses() {
        assertEquals(TAGS, tagsList.copyOfAllTagList())
        assertSameElementsIgnoreOrder(CHECKED_TAGS, tagsList.copyOfCheckedTagList())

        assertTrue(tagsList.toggleAllCheckedStatuses())

        assertEquals(TAGS, tagsList.copyOfAllTagList())
        assertSameElementsIgnoreOrder(TAGS, tagsList.copyOfCheckedTagList())

        assertTrue(tagsList.toggleAllCheckedStatuses())

        assertEquals(TAGS, tagsList.copyOfAllTagList())
        assertSameElementsIgnoreOrder(ArrayList(), tagsList.copyOfCheckedTagList())
    }

    @Test
    fun test_toggleAllCheckedStatuses_indeterminate() {
        assertEquals(TAGS, tagsListWithIndeterminate.copyOfAllTagList())
        assertSameElementsIgnoreOrder(
            minus(CHECKED_TAGS, INDETERMINATE_TAGS),
            tagsListWithIndeterminate.copyOfCheckedTagList(),
        )

        assertNotEquals(emptyList<Any>(), tagsListWithIndeterminate.copyOfIndeterminateTagList())

        assertTrue(tagsListWithIndeterminate.toggleAllCheckedStatuses())

        assertEquals(TAGS, tagsListWithIndeterminate.copyOfAllTagList())
        assertSameElementsIgnoreOrder(TAGS, tagsListWithIndeterminate.copyOfCheckedTagList())
        assertEquals(emptyList<Any>(), tagsListWithIndeterminate.copyOfIndeterminateTagList())

        assertTrue(tagsListWithIndeterminate.toggleAllCheckedStatuses())
        assertEquals(TAGS, tagsListWithIndeterminate.copyOfAllTagList())
        assertSameElementsIgnoreOrder(ArrayList(), tagsListWithIndeterminate.copyOfCheckedTagList())
        assertEquals(emptyList<Any>(), tagsListWithIndeterminate.copyOfIndeterminateTagList())
    }

    @Test
    fun test_size_if_checked_have_no_extra_items_not_found_in_allTags() {
        assertEquals(TAGS.size, tagsList.size())
        assertEquals(TAGS.size, tagsListWithIndeterminate.size())
    }

    @Test
    fun test_size_if_checked_have_extra_items_not_found_in_allTags() {
        tagsList = TagsList(TAGS, join(CHECKED_TAGS, "NEW"))
        assertEquals((TAGS.size + 1), tagsList.size())
    }

    @Test
    fun test_size_if_unchecked_and_checked_have_extra_items_not_found_in_allTags() {
        tagsList = TagsList(TAGS, join(CHECKED_TAGS, "NEW"), join(UNCHECKED_TAGS, "ALSO_NEW"))
        assertEquals((TAGS.size + 2), tagsList.size())
    }

    @Test
    fun test_sort() {
        assertEquals(TAGS, tagsList.copyOfAllTagList())
        tagsList.sort()
        assertEquals(
            "Calling #sort on TagsList should result on sorting all tags",
            SORTED_TAGS,
            tagsList.copyOfAllTagList(),
        )
    }

    @Test
    fun test_sort_with_indeterminate_tags() {
        assertEquals(TAGS, tagsListWithIndeterminate.copyOfAllTagList())
        tagsListWithIndeterminate.sort()
        assertEquals(
            "Calling #sort on TagsList should result on sorting all tags",
            SORTED_TAGS,
            tagsListWithIndeterminate.copyOfAllTagList(),
        )
    }

    @Test // #8807
    @Ignore(
        "Collections.singletonList() triggers infinite recursion. " +
            "Need solution to only mock the sort() method.",
    )
    fun test_sort_will_not_call_collectionsSort() {
        Mockito.mockStatic(Collections::class.java).use { mockCollection ->
            assertEquals(TAGS, tagsList.copyOfAllTagList())

            tagsList.sort()
            assertEquals(
                "Calling #sort on TagsList should result on sorting all tags",
                SORTED_TAGS,
                tagsList.copyOfAllTagList(),
            )

            mockCollection.verify({ Collections.sort(ArgumentMatchers.any(), ArgumentMatchers.any<Comparator<in Any>>()) }, Mockito.never())
        }
    }

    companion object {
        val SORTED_TAGS =
            listOf(
                "colors",
                "faces",
                "programming",
                "cars",
                "electrical",
                "flags",
                "learn",
                "meat",
                "names",
                "playground",
            )
        val TAGS =
            listOf(
                "programming",
                "learn",
                "names",
                "faces",
                "cars",
                "colors",
                "flags",
                "meat",
                "playground",
                "electrical",
            )
        val CHECKED_TAGS =
            listOf(
                "programming",
                "faces",
                "colors",
            )
        val UNCHECKED_TAGS =
            listOf(
                "electrical",
                "meat",
                "programming",
                "faces",
            )
        val INDETERMINATE_TAGS =
            listOf(
                "programming",
                "faces",
            )

        private fun <E> join(
            l1: List<E>,
            l2: List<E>,
        ): List<E> {
            val joined: MutableList<E> = ArrayList()
            joined.addAll(l1)
            joined.addAll(l2)
            return joined
        }

        private fun <E> join(
            l1: List<E>,
            e: E,
        ): List<E> {
            val joined: MutableList<E> = ArrayList(l1)
            joined.add(e)
            return joined
        }

        private fun <E> minus(
            l1: List<E>,
            e: E,
        ): List<E> {
            val res: MutableList<E> = ArrayList(l1)
            res.remove(e)
            return res
        }

        private fun <E> minus(
            l1: List<E>,
            el: List<E>,
        ): List<E> {
            val res: MutableList<E> = ArrayList(l1)
            for (e in el) {
                res.remove(e)
            }
            return res
        }

        private fun <E> assertSameElementsIgnoreOrder(
            l1: Collection<E>,
            l2: Collection<E>,
        ) {
            assertSameElementsIgnoreOrder(null, l1, l2)
        }

        private fun <E> assertSameElementsIgnoreOrder(
            message: String?,
            l1: Collection<E>,
            l2: Collection<E>,
        ) {
            assertEquals(message, l1.size, l2.size)
            assertTrue(message, l1.containsAll(l2))
        }
    }
}
