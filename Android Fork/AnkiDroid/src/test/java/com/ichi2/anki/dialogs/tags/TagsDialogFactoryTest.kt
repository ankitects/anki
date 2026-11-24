// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs.tags

import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.tags.TagsDialog.DialogType
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.anki.utils.ext.showDialogFragment
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class TagsDialogFactoryTest : RobolectricTest() {
    @Test
    fun `duplicate edit requests preserve preselected tags`() =
        withFactory { activity, factory ->
            factory.show(activity, checkedTags = arrayListOf("initial"))
            advanceRobolectricLooper()
            val dialog = activity.currentDialog
            val filesBeforeDuplicate = activity.cacheDir.listFiles()?.toSet()

            factory.show(activity, checkedTags = arrayListOf("replacement"))

            assertSame(dialog, activity.currentDialog)
            assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
            assertEquals(filesBeforeDuplicate, activity.cacheDir.listFiles()?.toSet())
            val tags = dialog.viewModel.tags.await()
            assertEquals(listOf("initial"), tags.copyOfCheckedTagList())
        }

    @Test
    fun `filter dialogs do not block editing tags`() = assertModeDoesNotBlockEditing(DialogType.FILTER_BY_TAG)

    @Test
    fun `custom study dialogs do not block editing tags`() = assertModeDoesNotBlockEditing(DialogType.CUSTOM_STUDY)

    private fun assertModeDoesNotBlockEditing(type: DialogType) =
        withFactory { activity, factory ->
            val previousDialog = TagsDialog(mock()).withArguments(activity, type)
            activity.showDialogFragment(previousDialog)
            advanceRobolectricLooper()

            factory.show(activity, checkedTags = arrayListOf("editing"))
            advanceRobolectricLooper()

            val editDialog = activity.currentDialog
            assertNotSame(previousDialog, editDialog)
            val tags = editDialog.viewModel.tags.await()
            assertTrue(tags.isChecked("editing"))
            assertTrue(editDialog.requireDialog().isShowing)
        }

    private val FragmentActivity.currentDialog: TagsDialog
        get() = assertIs<TagsDialog>(supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG))

    private fun withFactory(block: suspend (FragmentActivity, TagsDialogFactory) -> Unit) =
        runTest {
            ensureCollectionLoadIsSynchronous()
            Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
                controller.get().setTheme(CommonR.style.Theme_AnkiDroid)
                val activity = controller.setup().get()
                val factory = TagsDialogFactory(mock()).attachToActivity<TagsDialogFactory>(activity)
                block(activity, factory)
            }
        }
}
