// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tarek Mohamed <tarekkma@gmail.com>

package com.ichi2.anki.dialogs.tags

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.FragmentScenario
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.libanki.testutils.ext.newNote
import com.ichi2.anki.model.CardStateFilter
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.testutils.ParametersUtils
import com.ichi2.testutils.RecyclerViewUtils
import com.ichi2.testutils.parcelledCopy
import com.ichi2.testutils.saveState
import com.ichi2.ui.CheckBoxTriStates
import com.ichi2.ui.CheckBoxTriStates.State.CHECKED
import com.ichi2.utils.getInputField
import com.ichi2.utils.getInputTextLayout
import com.ichi2.utils.positiveButton
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThan
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import timber.log.Timber
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class TagsDialogTest : RobolectricTest() {
    @Test
    fun `confirming removal of a parent does not preserve its display state`() {
        confirmTags(listOf("B B::child"), parentClicks = 1, selected = listOf("B::child"), preserved = emptyList())
    }

    @Test
    fun `confirming unchanged partial selection preserves the original parent tag`() {
        confirmTags(listOf("B B::child", "B::child"), parentClicks = 0, selected = listOf("B::child"), preserved = listOf("B"))
    }

    @Test
    fun `confirming an overridden partial parent does not restore its original selection`() {
        confirmTags(listOf("B B::child", "B::child"), parentClicks = 2, selected = listOf("B::child"), preserved = emptyList())
    }

    @Test
    fun `confirming a partial child does not export a synthetic parent`() {
        confirmTags(listOf("B::child", ""), parentClicks = 0, selected = emptyList(), preserved = listOf("B::child"))
    }

    /** Opens the dialog for the supplied notes, clicks the parent tag, and verifies the confirmed selection. */
    private fun confirmTags(
        noteTags: List<String>,
        parentClicks: Int,
        selected: List<String>,
        preserved: List<String>,
    ) {
        val ids =
            noteTags.mapIndexed { index, tags ->
                addBasicNote("note $index").id.also { col.tags.bulkAdd(listOf(it), tags) }
            }
        val listener = Mockito.mock(TagsDialogListener::class.java)
        val args = TagsDialog().withArguments(targetContext, TagsDialog.DialogType.EDIT_TAGS, ids).requireArguments()
        FragmentScenario.launch(TagsDialog::class.java, args, CommonR.style.Theme_AnkiDroid, TagsDialogFactory(listener)).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onFragment { fragment ->
                advanceRobolectricLooperUntil { fragment.binding.tagsList.adapter != null }
                val recycler = fragment.forceLayout()
                val parent = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 0)
                repeat(parentClicks) { parent.checkBoxView.performClick() }
                (fragment.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                advanceRobolectricLooper()
                Mockito.verify(listener).onSelectedTags(selected, preserved, CardStateFilter.ALL_CARDS)
            }
        }
    }

    @Test
    fun `missing selection dismisses tags dialog without submitting`() = assertUnavailableSelection { assertTrue(delete()) }

    @Test
    fun `truncated selection dismisses tags dialog without submitting`() =
        assertUnavailableSelection {
            writeBytes(readBytes().dropLast(1).toByteArray())
        }

    private fun assertUnavailableSelection(changeFile: IdsFile.() -> Unit) {
        val listener = Mockito.mock(TagsDialogListener::class.java)
        val fragment = TagsDialog(listener).withArguments(targetContext, TagsDialog.DialogType.EDIT_TAGS, listOf(addBasicNote().id))
        fragment.requireArguments().requireParcelable<IdsFile>(TagsDialog.ARG_TAGS_FILE).changeFile()
        Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
            controller.get().setTheme(CommonR.style.Theme_AnkiDroid)
            val activity = controller.setup().get()
            fragment.show(activity.supportFragmentManager, "tags")
            advanceRobolectricLooper()
            assertNull(activity.supportFragmentManager.findFragmentByTag("tags"))
            assertEquals(targetContext.getString(CommonString.something_wrong), ShadowToast.getTextOfLatestToast())
            Mockito.verifyNoInteractions(listener)
        }
    }

    @Test
    fun `restoring after selection loss dismisses tags dialog`() {
        val fragment = TagsDialog().withArguments(targetContext, TagsDialog.DialogType.EDIT_TAGS, listOf(addBasicNote().id))
        val file = fragment.requireArguments().requireParcelable<IdsFile>(TagsDialog.ARG_TAGS_FILE)
        val savedState =
            Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
                controller.get().setTheme(CommonR.style.Theme_AnkiDroid)
                val activity = controller.setup().get()
                fragment.show(activity.supportFragmentManager, "tags")
                advanceRobolectricLooper()
                controller.saveState().parcelledCopy(FragmentActivity::class.java.classLoader)
            }
        assertTrue(file.delete())
        ShadowToast.reset()
        Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
            controller.get().setTheme(CommonR.style.Theme_AnkiDroid)
            val activity = controller.setup(savedState).get()
            advanceRobolectricLooper()
            assertNull(activity.supportFragmentManager.findFragmentByTag("tags"))
            assertEquals(targetContext.getString(CommonString.something_wrong), ShadowToast.getTextOfLatestToast())
        }
    }

    /** The hierarchy policy is covered in TagTreeTest; check the dialog-to-adapter update here. */
    @Test
    fun `adding a tag refreshes its visible checked row`() {
        val args = editTagsArguments(listOf("parent::existing"))
        runTagsDialogScenario(args) { fragment ->
            fragment.addTag("parent::added")
            val recycler = fragment.forceLayout()
            val rows =
                (0 until recycler.adapter!!.itemCount)
                    .map { RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, it) }
            assertEquals(listOf("parent", "parent::added", "parent::existing"), rows.map { it.text })
            assertEquals(CHECKED, rows[1].checkboxState)
        }
    }

    @Test // #11089
    fun test_SearchTag_spaceWillBeFilteredCorrectly() {
        val type = TagsDialog.DialogType.FILTER_BY_TAG
        val allTags = listOf("hello::world")
        val checkedTags = arrayListOf<String>()
        val args =
            TagsDialog(ParametersUtils.whatever())
                .withTestArguments(type, checkedTags, allTags)
                .requireArguments()
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        runTagsDialogScenario(args, factory) { f: TagsDialog ->
            val toolbar = f.binding.toolbar.root
            val searchView = toolbar.menu.findItem(R.id.tags_dialog_action_filter).actionView!!
            val editText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)!!

            editText.setText("hello ")
            Assert.assertEquals(
                "The space should be replaced by '::' without mistakenly clear everything.",
                "hello::",
                editText.text.toString(),
            )

            editText.setText("hello")
            editText.text.insert(5, " ")
            Assert.assertEquals("Should complete 2 colons.", "hello::", editText.text.toString())

            editText.setText("hello:")
            editText.text.insert(6, " ")
            Assert.assertEquals("Should complete 1 colon.", "hello::", editText.text.toString())

            editText.setText("hello::")
            editText.text.insert(7, " ")
            Assert.assertEquals("Should do nothing.", "hello::", editText.text.toString())

            editText.setText("")
            editText.text.insert(0, " ")
            Assert.assertEquals("Should not crash.", "::", editText.text.toString())
        }
    }

    @Test
    fun `unicode tags can be serialized 16576`() {
        val type = TagsDialog.DialogType.FILTER_BY_TAG
        val allTags = listOf("02动作状态")

        val args =
            TagsDialog(ParametersUtils.whatever())
                .withTestArguments(type, arrayListOf(), allTags)
                .arguments
        val mockListener = Mockito.mock(TagsDialogListener::class.java)
        val factory = TagsDialogFactory(mockListener)
        FragmentScenario.launch(TagsDialog::class.java, args, CommonR.style.Theme_AnkiDroid, factory).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onFragment { Timber.d("Dialog successfully opened") }
        }
    }

    @Test
    fun `squeezed dialog does not inflate every tag 22082`() {
        val allTags = (1..50).map { "tag$it" }
        val args =
            TagsDialog(ParametersUtils.whatever())
                .withTestArguments(TagsDialog.DialogType.FILTER_BY_TAG, arrayListOf(), allTags)
                .requireArguments()
        runTagsDialogScenario(args) { f: TagsDialog ->
            val recycler = f.binding.tagsList
            val content = f.binding.root

            content.measure(
                View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(50, View.MeasureSpec.AT_MOST),
            )
            content.layout(0, 0, content.measuredWidth, content.measuredHeight)

            assertThat(recycler.height, greaterThanOrEqualTo(0))
            assertThat(recycler.childCount, lessThan(allTags.size))
        }
    }

    @Test
    @Config(qualifiers = "w731dp-h411dp")
    fun `keyboard pans the dialog on a short screen 22082`() {
        runTagsDialogScenario(editTagsArguments()) { f: TagsDialog ->
            assertThat(f.softInputAdjustment, equalTo(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN))
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp")
    fun `keyboard fits the dialog using IME insets on a tall screen`() {
        runTagsDialogScenario(editTagsArguments()) { f: TagsDialog ->
            assertThat(f.softInputAdjustment, equalTo(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING))
            assertThat(f.fitsKeyboardInsets, equalTo(true))
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp")
    fun `rotation switches between fitting the IME and panning 22082`() {
        runTagsDialogScenario(editTagsArguments()) { f: TagsDialog ->
            val portrait = Configuration(f.resources.configuration)
            val landscape =
                Configuration(portrait).apply {
                    screenWidthDp = 914
                    screenHeightDp = 411
                    orientation = Configuration.ORIENTATION_LANDSCAPE
                }
            f.onConfigurationChanged(landscape)

            assertThat(f.softInputAdjustment, equalTo(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN))
            assertThat(f.fitsKeyboardInsets, equalTo(false))

            f.onConfigurationChanged(portrait)
            assertThat(f.softInputAdjustment, equalTo(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING))
            assertThat(f.fitsKeyboardInsets, equalTo(true))
        }
    }

    @get:SuppressLint("NewApi") // These dialog tests run on the target SDK (API 30+).
    private val TagsDialog.fitsKeyboardInsets: Boolean
        get() = requireDialog().window!!.attributes.fitInsetsTypes and WindowInsets.Type.ime() != 0

    private val TagsDialog.softInputAdjustment: Int
        get() = requireDialog().window!!.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

    private fun editTagsArguments(allTags: List<String> = listOf("a")) =
        TagsDialog(ParametersUtils.whatever())
            .withTestArguments(TagsDialog.DialogType.EDIT_TAGS, arrayListOf(), allTags)
            .requireArguments()

    // these are called 'withTestArguments' due to "extension is shadowed by a member" warnings
    // this is needed so we can pass in 'targetContext' for context.cacheDir
    private fun TagsDialog.withTestArguments(
        type: TagsDialog.DialogType,
        checkedTags: ArrayList<String>,
        allTags: Collection<String>,
    ): TagsDialog {
        val note = col.newNote()
        col.tags.bulkAdd(listOf(note.id), allTags.joinToString(separator = " "))
        col.addNote(note, 0L)
        return withArguments(
            context = targetContext,
            type = type,
            checkedTags = checkedTags,
        )
    }

    @Test
    fun `entering an existing tag shows an error and disables OK`() {
        val existingTag = "a"
        val validTag = "b"
        withAddTagDialog(allTags = listOf(existingTag)) { addTagDialog ->
            // OK starts disabled: Ensure its enabled for a valid input
            addTagDialog.getInputField().setText(validTag)
            advanceRobolectricLooperUntil(
                lazyMessage = { "OK was never enabled for a valid tag" },
            ) {
                addTagDialog.positiveButton.isEnabled
            }
            addTagDialog.getInputField().setText(existingTag)
            advanceRobolectricLooperUntil(
                lazyMessage = { "error was never shown for an existing tag" },
            ) {
                addTagDialog.getInputTextLayout().error != null
            }
            assertEquals(
                expected = targetContext.getString(CommonString.tag_already_exists),
                actual = addTagDialog.getInputTextLayout().error.toString(),
            )
            assertFalse(
                actual = addTagDialog.positiveButton.isEnabled,
                message = "OK should be disabled for an existing tag",
            )
        }
    }

    @Test
    fun `entering a new valid tag clears the error and enables OK`() {
        val existingTag = "a"
        val validTag = "b"
        withAddTagDialog(allTags = listOf(existingTag)) { addTagDialog ->
            // Start from the error state
            addTagDialog.getInputField().setText(existingTag)
            advanceRobolectricLooperUntil(
                lazyMessage = { "error was never shown for an existing tag" },
            ) {
                addTagDialog.getInputTextLayout().error != null
            }
            addTagDialog.getInputField().setText(validTag)
            advanceRobolectricLooperUntil(
                lazyMessage = { "error was never cleared for a valid tag" },
            ) {
                addTagDialog.getInputTextLayout().error == null
            }
            assertTrue(
                actual = addTagDialog.positiveButton.isEnabled,
                message = "OK should be enabled for a valid tag",
            )
        }
    }

    @Test
    fun `long-clicking a tag opens the add tag dialog prefilled with the tag as a prefix`() {
        val tag = "parent"
        runTagsDialogScenario(args = editTagsArguments(listOf(tag))) { tagsDialog ->
            val recycler = tagsDialog.forceLayout()
            val row = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 0)
            row.itemView.performLongClick()
            advanceRobolectricLooper()
            val addTagDialog = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals("$tag::", addTagDialog.getInputField().text.toString())
        }
    }

    @Test
    fun `long-clicking a tag is ignored in every dialog type except EDIT_TAGS`() {
        // CUSTOM_STUDY only lists the tags of the notes passed in, so pass a tagged note
        val noteId =
            addBasicNote().id.also {
                col.tags.bulkAdd(
                    noteIds = listOf(it),
                    tags = "tag",
                )
            }
        for (type in TagsDialog.DialogType.entries - TagsDialog.DialogType.EDIT_TAGS) {
            val args =
                TagsDialog()
                    .withArguments(
                        context = targetContext,
                        type = type,
                        noteIds = listOf(noteId),
                    ).requireArguments()
            runTagsDialogScenario(args = args) { tagsDialog ->
                val recycler = tagsDialog.forceLayout()
                val row = RecyclerViewUtils.viewHolderAt<TagsArrayAdapter.ViewHolder>(recycler, 0)
                val dialogBefore = ShadowDialog.getLatestDialog()
                val consumed = row.itemView.performLongClick()
                advanceRobolectricLooper()
                assertFalse(
                    actual = consumed,
                    message = "$type: the long click should not be consumed",
                )
                assertEquals(
                    expected = dialogBefore,
                    actual = ShadowDialog.getLatestDialog(),
                    message = "$type: no add tag dialog should be shown",
                )
            }
        }
    }

    private fun withAddTagDialog(
        allTags: List<String>,
        block: (AlertDialog) -> Unit,
    ) {
        runTagsDialogScenario(args = editTagsArguments(allTags)) { tagsDialog ->
            tagsDialog.binding.toolbar.root.menu
                .performIdentifierAction(R.id.tags_dialog_action_add, 0)
            advanceRobolectricLooper()
            block(ShadowDialog.getLatestDialog() as AlertDialog)
        }
    }

    private fun runTagsDialogScenario(
        args: Bundle,
        factory: TagsDialogFactory? = null,
        block: (TagsDialog) -> Unit,
    ) {
        FragmentScenario.launch(TagsDialog::class.java, args, CommonR.style.Theme_AnkiDroid, factory).use { scenario ->
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.onFragment { tagsDialog: TagsDialog ->
                advanceRobolectricLooperUntil { tagsDialog.binding.tagsList.adapter != null }
                block(tagsDialog)
            }
        }
    }
}

/**
 * Forces layout so Robolectric creates the tag row.
 * Workaround for the robolectric recyclerView issue.
 */
private fun TagsDialog.forceLayout(height: Int = 1000): RecyclerView =
    binding.tagsList.also {
        it.measure(0, 0)
        it.layout(0, 0, 100, height)
    }

val TagsArrayAdapter.ViewHolder.checkBoxView: CheckBoxTriStates
    get() = this.binding.checkBoxView
