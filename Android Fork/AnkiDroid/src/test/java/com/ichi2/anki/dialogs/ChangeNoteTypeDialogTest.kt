// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.content.Context
import android.os.Bundle
import android.os.Looper.getMainLooper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.Spinner
import androidx.fragment.app.testing.launchFragment
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.dialogs.ChangeNoteTypeDialog.Companion.ARG_NOTE_IDS
import com.ichi2.anki.libanki.NoteId
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class ChangeNoteTypeDialogTest : RobolectricTest() {
    @Test
    fun `no changes failure has a translated message`() {
        assertThat(ChangeNoteTypeException.Kind.NO_CHANGES.toString(targetContext), equalTo("No changes to save"))
    }

    /**
     * #20944: with an accessibility service enabled, a Spinner reports its initial selection.
     * A template selection is meaningless for cloze, and used to be fatal
     */
    @Test
    fun `templates tab for a cloze note does not crash with an accessibility service`() {
        enableAccessibilityService()

        withChangeNoteTypeDialog(addClozeNote("{{c1::Hi}}").id) {
            selectTemplatesTab()
        }
        // a crash is reported by FailOnUnhandledExceptionRule
    }

    /** #20944: spinners which can't be used still deliver selections, which used to be fatal */
    @Test
    fun `no template spinners are built for a cloze note type`() =
        withChangeNoteTypeDialog(addClozeNote("{{c1::Hi}}").id) {
            assertThat("templates are locked", templatesContainer().childCount, equalTo(0))
        }

    @Test
    fun `template spinners are built for a regular note type`() =
        withChangeNoteTypeDialog(addBasicNote().id) {
            assertThat("a spinner per output template", templatesContainer().childCount, equalTo(1))
        }

    @Test
    fun `template spinners are rebuilt after visiting cloze`() =
        withChangeNoteTypeDialog(addBasicNote().id) {
            val container = templatesContainer()

            selectNoteType("Cloze")
            selectNoteType("Basic (and reversed card)")

            assertThat("shown for regular -> regular", container.visibility, equalTo(View.VISIBLE))
            assertThat("one spinner per output template", container.childCount, equalTo(2))
        }

    /** The template mapping rows, which only apply to a regular -> regular conversion */
    private fun ChangeNoteTypeDialog.templatesContainer(): ViewGroup {
        // the templates tab is the second page of the pager
        val pager = requireView().findViewById<View>(R.id.change_note_type_pager)
        assertThat("the dialog has loaded", pager.visibility, equalTo(View.VISIBLE))
        selectTemplatesTab()
        return requireNotNull(requireView().findViewById(R.id.templates_container)) {
            "templates_container was not created"
        }
    }

    private fun ChangeNoteTypeDialog.selectTemplatesTab() {
        requireView()
            .findViewById<ViewPager2>(R.id.change_note_type_pager)
            .setCurrentItem(ChangeNoteTypeViewModel.Tab.Templates.position, false)
        shadowOf(getMainLooper()).idle()
    }

    /** Picks a note type in the 'new note type' spinner, as a user would */
    private fun ChangeNoteTypeDialog.selectNoteType(name: String) {
        val spinner = requireView().findViewById<Spinner>(R.id.dest_note_type_spinner)
        val id = requireNotNull(col.notetypes.byName(name)) { "note type '$name' not found" }.id
        val position = (0 until spinner.count).first { spinner.adapter.getItemId(it) == id }
        spinner.setSelection(position)
        shadowOf(getMainLooper()).idle()
    }

    /** Makes [AccessibilityManager.isEnabled] return true, as it would with TalkBack running */
    private fun enableAccessibilityService() {
        val manager = targetContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        shadowOf(manager).setEnabled(true)
    }

    private fun withChangeNoteTypeDialog(
        noteId: NoteId,
        action: ChangeNoteTypeDialog.() -> Unit,
    ) {
        launchFragment<ChangeNoteTypeDialog>(
            fragmentArgs = Bundle().apply { putLongArray(ARG_NOTE_IDS, longArrayOf(noteId)) },
            themeResId = CommonR.style.Theme_AnkiDroid,
        ).use { scenario -> scenario.onFragment { action(it) } }
    }
}
