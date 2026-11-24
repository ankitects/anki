// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs.tags

import androidx.core.view.isVisible
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.DialogScreenshotTest
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.tags.TagsDialog.DialogType
import org.junit.Test

class TagsDialogScreenshotTest : DialogScreenshotTest() {
    @Test
    fun `empty tags`() =
        runTest {
            val dialog = TagsDialog().withArguments(targetContext, DialogType.EDIT_TAGS)
            withDialog(dialog) {
                dialog.viewModel.tags.await()
                advanceRobolectricLooperUntil { dialog.binding.noTagsTextView.isVisible }
                captureScreen("empty_tags")
            }
        }

    @Test
    fun `tag selection and search`(
        @TestParameter type: DialogType,
        @TestParameter landscape: Boolean,
    ) = runTest {
        val orientation = setOrientation(landscape)
        val name = "${orientation}_${type.name.lowercase()}"
        val note = addBasicNote()
        col.tags.bulkAdd(
            listOf(note.id),
            "biology::animals biology::plants chemistry geography history languages::English mathematics physics revision::priority",
        )
        val dialog =
            TagsDialog().withArguments(
                targetContext,
                type,
                noteIds = if (type == DialogType.CUSTOM_STUDY) listOf(note.id) else emptyList(),
                checkedTags = arrayListOf("biology::plants", "chemistry"),
            )
        withDialog(dialog) {
            dialog.viewModel.tags.await()
            advanceRobolectricLooperUntil { dialog.binding.tagsList.adapter != null }
            captureScreen(name)

            captureSearchResults(
                name,
                list = dialog.binding.tagsList,
                searchItem =
                    dialog.binding.toolbar.root.menu
                        .findItem(R.id.tags_dialog_action_filter),
                query = "biology",
            )
        }
    }
}
