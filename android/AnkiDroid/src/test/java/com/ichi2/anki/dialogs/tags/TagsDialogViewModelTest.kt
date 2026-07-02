/*
 * Copyright (c) 2025 Divyansh Kushwaha <thedroiddiv@gmail.com>
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

package com.ichi2.anki.dialogs.tags

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.testutils.ext.newNote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.properties.Delegates

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TagsDialogViewModelTest : RobolectricTest() {
    private var note1 by Delegates.notNull<NoteId>()
    private var note2 by Delegates.notNull<NoteId>()

    private val testDispatcher = StandardTestDispatcher()

    /**
     * Initialize the collections with three notes with each having some tags
     * ```
     * note1    a,b,c
     * note2    b,d
     * note3    e,f
     * ```
     */
    @Before
    fun setupNotes() {
        setupTestDispatcher(testDispatcher)

        val n1 = col.newNote()
        col.addNote(n1, 0)
        col.tags.bulkAdd(listOf(n1.id), "a b c")
        note1 = n1.id

        val n2 = col.newNote()
        col.addNote(n2, 0)
        col.tags.bulkAdd(listOf(n2.id), "b d")
        note2 = n2.id

        val n3 = col.newNote()
        col.addNote(n3, 0)
        col.tags.bulkAdd(listOf(n3.id), "e f")
    }

    @Test
    fun `single note produces correct checked, indeterminate and all sets`() =
        runTest(testDispatcher) {
            val vm =
                TagsDialogViewModel(
                    noteIds = listOf(note1),
                    checkedTags = listOf("a"),
                    isCustomStudying = false,
                )
            val tags = vm.tags.await()
            // only one note, tags can either be checked or unchecked only
            // all : {a,b,c,d,e,f}
            // indeterminate: { }
            // checked: {a,b,c}
            assertEquals(listOf("a", "b", "c"), tags.copyOfCheckedTagList().sorted())
            assertTrue(tags.copyOfIndeterminateTagList().isEmpty())
            assertEquals(listOf("a", "b", "c", "d", "e", "f"), tags.copyOfAllTagList().sorted())
        }

    @Test
    fun `multiple notes create indeterminate tags correctly`() =
        runTest(testDispatcher) {
            // note 1           a, b, c
            // note 2              b, d
            val vm =
                TagsDialogViewModel(
                    noteIds = listOf(note1, note2),
                    checkedTags = emptyList(),
                    isCustomStudying = false,
                )

            val tags = vm.tags.await()
            val checked = tags.copyOfCheckedTagList()
            val ind = tags.copyOfIndeterminateTagList()

            // All tags in the collection: {a,b,c,d,e,f}
            // Assuming indeterminate logic in [TagsList] is correct
            // a    [-] - indeterminate
            // b    [x] - checked
            // c    [-] - indeterminate
            // d    [-] - indeterminate
            // e    [ ] - unchecked
            // f    [ ] - unchecked
            assertEquals(listOf("b"), checked.sorted())
            assertEquals(listOf("a", "c", "d"), ind.sorted())
            assertFalse(tags.isChecked("e"))
            assertFalse(tags.isChecked("f"))
        }

    @Test
    fun `extra checkedTags is absolute checked and not indeterminate`() =
        runTest(testDispatcher) {
            val vm =
                TagsDialogViewModel(
                    noteIds = listOf(note1),
                    checkedTags = listOf("d", "x"),
                    isCustomStudying = false,
                )

            // note1.tags       {a,b}
            // extraCheckedTags {d, x}
            // allTags          {a,b,c,d,e}
            // implies inside dialog
            // checkedTags      {a, b, d, x}
            // uncheckedTags    { c, e }
            // indeterminate    { }
            val tags = vm.tags.await()
            assertTrue(tags.isChecked("d"))
            assertFalse(tags.isIndeterminate("d"))
            assertTrue(tags.isChecked("x"))
            assertFalse(tags.isIndeterminate("d"))
            assertTrue(tags.copyOfAllTagList().contains("x"))
        }

    @Test
    fun `custom study mode makes all tags unchecked`() =
        runTest(testDispatcher) {
            val vm =
                TagsDialogViewModel(
                    noteIds = listOf(note1, note2),
                    checkedTags = listOf("a"),
                    isCustomStudying = true,
                )
            val tags = vm.tags.await()
            // tags from both notes = {a,b,c,d}
            val all = tags.copyOfAllTagList().sorted()
            assertEquals(listOf("a", "b", "c", "d"), all)
            // custom study: checked = empty
            assertTrue(tags.copyOfCheckedTagList().isEmpty())
            // custom study: indeterminate = empty
            assertTrue(tags.copyOfIndeterminateTagList().isEmpty())
            // custom study:
            // checked - empty, unchecked - empty, all - not empty
            // implies unchecked = allTags
            assertTrue(tags.copyOfAllTagList().isNotEmpty())
        }
}
