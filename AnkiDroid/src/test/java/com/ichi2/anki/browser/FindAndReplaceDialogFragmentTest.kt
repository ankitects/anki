/*
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
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

package com.ichi2.anki.browser

import android.content.Context
import android.widget.Spinner
import android.widget.SpinnerAdapter
import androidx.core.os.bundleOf
import androidx.fragment.app.testing.FragmentScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.ui.internationalization.toSentenceCase
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FindAndReplaceDialogFragmentTest : RobolectricTest() {
    @Test
    fun `with no selected notes 'only selected notes' check box is not actionable`() =
        runTest {
            onFindReplaceFragment(emptyList()) {
                // nothing selected so checkbox 'Only selected notes' should be unchecked and not enabled
                onView(withId(R.id.only_selected_notes_check_box))
                    .inRoot(isDialog())
                    .check(matches(isNotEnabled()))
                onView(withId(R.id.only_selected_notes_check_box))
                    .inRoot(isDialog())
                    .check(matches(isNotChecked()))
                onView(withId(R.id.ignore_case_check_box))
                    .inRoot(isDialog())
                    .check(matches(isChecked()))
                // using input as regex is not checked at start
                onView(withId(R.id.input_as_regex_check_box))
                    .inRoot(isDialog())
                    .check(matches(isNotChecked()))
                // as nothing is selected the target selector has only the two default options
                assertThat(adapter.items, equalTo(targetContext.getDefaultTargets()))
            }
        }

    @Test
    fun `shows expected find-replace targets for selected notes`() =
        runTest {
            val n1 = createFindReplaceTestNote("A", "Car", "Lion")
            val n2 = createFindReplaceTestNote("B", "Train", "Chicken")
            createFindReplaceTestNote("C", "Plane", "Vulture")
            onFindReplaceFragment(listOf(n1.id, n2.id)) {
                // 2 default field options + 4 fields(2 from each of the two notes selected above)
                // see createFindReplaceTestNote for test field names
                val allTargets =
                    targetContext.getDefaultTargets() +
                        listOf("Afield0", "Afield1", "Bfield0", "Bfield1")
                assertThat(adapter.items, equalTo(allTargets))
            }
        }

    // see #19929
    @Test
    fun `'selected notes only' status is correct after fragment restore`() =
        runTest {
            val note = createFindReplaceTestNote("A", "kart", "kilogram")
            val file = IdsFile(targetContext.cacheDir, listOf(note.id))
            FragmentScenario
                .launch(
                    fragmentClass = FindAndReplaceDialogFragment::class.java,
                    fragmentArgs = bundleOf(FindAndReplaceDialogFragment.ARG_IDS to file),
                    themeResId = R.style.Theme_Light,
                ).use { scenario ->
                    scenario.onFragment { fragment ->
                        advanceUntilIdle()
                        onView(withId(R.id.only_selected_notes_check_box))
                            .inRoot(isDialog())
                            .check(matches(isEnabled()))
                        onView(withId(R.id.only_selected_notes_check_box))
                            .inRoot(isDialog())
                            .check(matches(isChecked()))
                        // 2 default field options + 2 fields from the only note selected
                        val allTargets =
                            targetContext.getDefaultTargets() + listOf("Afield0", "Afield1")
                        assertThat(fragment.adapter.items, equalTo(allTargets))
                    }
                    scenario.recreate()
                    scenario.onFragment { fragment ->
                        advanceUntilIdle()
                        onView(withId(R.id.only_selected_notes_check_box))
                            .inRoot(isDialog())
                            .check(matches(isEnabled()))
                        onView(withId(R.id.only_selected_notes_check_box))
                            .inRoot(isDialog())
                            .check(matches(isChecked()))
                        // check that the target list from before the recreate call wasn't reset
                        val allTargets = targetContext.getDefaultTargets() + listOf("Afield0", "Afield1")
                        assertThat(fragment.adapter.items, equalTo(allTargets))
                    }
                }
        }

    private fun onFindReplaceFragment(
        noteIds: List<NoteId>,
        action: FindAndReplaceDialogFragment.() -> Unit,
    ) {
        val file = IdsFile(targetContext.cacheDir, noteIds)
        FragmentScenario
            .launch(
                fragmentClass = FindAndReplaceDialogFragment::class.java,
                fragmentArgs = bundleOf(FindAndReplaceDialogFragment.ARG_IDS to file),
                themeResId = R.style.Theme_Light,
            ).use { scenario -> scenario.onFragment { fragment -> fragment.action() } }
    }

    /**
     * 3 notetypes available(named A, B and C) each with two fields.
     * Fields names follow the pattern: "${NotetypeName}field${0/1}" (ex: "Afield1").
     * "C" notetype has the same name for field 1 as notetype B!
     * second is a [Pair] representing the field data(the note has only two fields)
     */
    private fun createFindReplaceTestNote(
        notetypeName: String,
        field0: String,
        field1: String,
    ): Note {
        addStandardNoteType("A", arrayOf("Afield0", "Afield1"), "", "")
        addStandardNoteType("B", arrayOf("Bfield0", "Bfield1"), "", "")
        addStandardNoteType("C", arrayOf("Cfield0", "Bfield1"), "", "")
        return addNoteUsingNoteTypeName(notetypeName, field0, field1)
    }

    val FindAndReplaceDialogFragment.adapter: SpinnerAdapter
        get() = dialog!!.findViewById<Spinner>(R.id.fields_selector).adapter as SpinnerAdapter

    private val SpinnerAdapter.items: List<String>
        get() =
            mutableListOf<String>().apply {
                for (position in 0 until count) {
                    add(getItem(position) as String)
                }
            }

    private fun Context.getDefaultTargets() =
        listOf(
            TR.browsingAllFields().toSentenceCase(this, R.string.sentence_all_fields),
            TR.editingTags(),
        )
}
