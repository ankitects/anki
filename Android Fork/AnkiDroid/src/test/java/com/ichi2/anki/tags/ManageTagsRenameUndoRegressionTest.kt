// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.rules.IcuCaseFoldingRule
import com.ichi2.testutils.withViewModel
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ManageTagsRenameUndoRegressionTest : JvmTest() {
    @get:Rule
    val caseFolding = IcuCaseFoldingRule()

    @Test
    fun `undoing an expanded branch rename removes the destination from the tag cache`() =
        runTest {
            val source = requireNotNull(TagName.build("source"))
            val destination = requireNotNull(TagName.build("destination"))
            val note = addBasicNote().update { addTag("source::child") }
            withViewModel(ManageTagsViewModel()) {
                toggleCollapsed(source).join()
                val originalTags = col.tags.all().sorted()
                renameTag(source, destination).join()
                assertEquals(listOf("destination::child"), col.getNote(note.id).tags)

                col.undo()

                assertEquals(listOf("source::child"), col.getNote(note.id).tags)
                assertEquals(originalTags, col.tags.all().sorted(), "Undo must also remove the unused destination parent")
            }
        }
}
