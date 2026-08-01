// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.scheduling

import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.launchFragment
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.libanki.sched.SetDueDateDays
import com.ichi2.anki.scheduling.SetDueDateViewModel.Tab
import com.ichi2.utils.positiveButton
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@NeedsTest("set interval to same value visibility with FSRS")
@RunWith(AndroidJUnit4::class)
class SetDueDateDialogTest : RobolectricTest() {
    @Test
    fun `switch tabs`() =
        testDialog {
            selectTab(0)
            assertThat(viewModel.currentTab, equalTo(Tab.SINGLE_DAY))
            selectTab(1)
            assertThat(viewModel.currentTab, equalTo(Tab.DATE_RANGE))
        }

    @Test
    fun `initial suffix is set`() =
        testDialog {
            selectTab(0)
            assertThat(singleDayTextLayout.suffixText, equalTo("days"))
            selectTab(1)
            assertThat(dateRangeStartLayout.suffixText, equalTo("days"))
            assertThat(dateRangeEndLayout.suffixText, equalTo("days"))
        }

    @Test
    fun `set single day`() =
        testDialog {
            selectTab(0)
            assertThat(positiveButtonIsEnabled, equalTo(false))
            singleDayText.setText("1")
            assertThat(positiveButtonIsEnabled, equalTo(true))
        }

    @Test
    fun `set date range`() =
        testDialog {
            selectTab(1)
            assertThat(positiveButtonIsEnabled, equalTo(false))
            dateRangeStart.setText("1")
            dateRangeEnd.setText("5")
            assertThat(positiveButtonIsEnabled, equalTo(true))
        }

    @Test
    fun `set update interval`() =
        testDialog {
            assertThat(viewModel.updateIntervalToMatchDueDate, equalTo(false))
            changeInterval.isChecked = true
            assertThat(viewModel.updateIntervalToMatchDueDate, equalTo(true))
        }

    @Test
    fun `singular text`() =
        testDialog(cardCount = 1) {
            selectTab(0)
            assertThat(dateSingleLabel.text, equalTo("Show card in"))
            selectTab(1)
            assertThat(dateRangeLabel.text, equalTo("Show card in range"))
        }

    @Test
    fun `plural text`() =
        testDialog(cardCount = 2) {
            selectTab(0)
            assertThat(dateSingleLabel.text, equalTo("Show cards in"))
            selectTab(1)
            assertThat(dateRangeLabel.text, equalTo("Show cards in range"))
        }

    @Test
    fun `integration test`() =
        testDialog {
            assertThat(viewModel.updateIntervalToMatchDueDate, equalTo(false))
            selectTab(1)
            dateRangeStart.setText("1")
            dateRangeEnd.setText("2")
            changeInterval.isChecked = true

            assertThat(viewModel.calculateDaysParameter(), equalTo(SetDueDateDays("1-2!")))
        }

    @Test
    fun `single day input limited to 5 digits`() =
        testDialog {
            selectTab(0)
            singleDayText.setText("123456")
            assertThat(singleDayText.text.toString(), equalTo("12345"))
        }

    @Test
    fun `range start input limited to 5 digits`() =
        testDialog {
            selectTab(1)
            dateRangeStart.setText("123456")
            assertThat(dateRangeStart.text.toString(), equalTo("12345"))
        }

    @Test
    fun `range end input limited to 5 digits`() =
        testDialog {
            selectTab(1)
            dateRangeEnd.setText("123456")
            assertThat(dateRangeEnd.text.toString(), equalTo("12345"))
        }

    private fun testDialog(
        cardCount: Int = 1,
        action: SetDueDateDialog.() -> Unit,
    ) = runTest {
        val cardIds = List(cardCount) { addBasicNote().firstCard().id }
        val dialog = SetDueDateDialog.newInstance(cardIds)
        launchFragment(
            themeResId = R.style.Base_Theme_Light,
            fragmentArgs = dialog.arguments,
        ) {
            return@launchFragment dialog
        }.use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            advanceRobolectricLooper()
            scenario.onFragment {
                action(it)
            }
        }
    }
}

/**
 * Selects a tab by index
 *
 * @throws IllegalArgumentException if index is invalid
 */
fun TabLayout.selectTab(index: Int) =
    requireNotNull(getTabAt(index))
        { "Tab $index not found" }
        .also { tab -> selectTab(tab) }

/**
 * Selects a tab by index, and waits for the [androidx.viewpager2.adapter.FragmentStateAdapter]
 * to attach the page's fragment view to the dialog's view hierarchy.
 */
fun SetDueDateDialog.selectTab(index: Int) {
    val viewPager = dialog!!.findViewById<ViewPager2>(R.id.set_due_date_pager)
    viewPager.setCurrentItem(index, false)
    // FragmentStateAdapter attaches fragments asynchronously via the main looper
    advanceRobolectricLooper()
}

val SetDueDateDialog.positiveButtonIsEnabled get() =
    (dialog as AlertDialog).positiveButton.isEnabled

val SetDueDateDialog.singleDayTextLayout: TextInputLayout get() =
    dialog!!.findViewById(R.id.set_due_date_single_day_input_layout)

val SetDueDateDialog.singleDayText: EditText get() = singleDayTextLayout.editText!!

val SetDueDateDialog.dateRangeStartLayout: TextInputLayout get() =
    dialog!!.findViewById(R.id.date_range_start_layout)

val SetDueDateDialog.dateRangeStart: EditText get() =
    dateRangeStartLayout.editText!!

val SetDueDateDialog.dateRangeEndLayout: TextInputLayout get() =
    dialog!!.findViewById(R.id.date_range_end_layout)

val SetDueDateDialog.dateRangeEnd: EditText get() =
    dateRangeEndLayout.editText!!

val SetDueDateDialog.changeInterval: CheckBox get() =
    dialog!!.findViewById(R.id.change_interval)!!

val SetDueDateDialog.dateRangeLabel: TextView get() =
    dialog!!.findViewById(R.id.date_range_label)

val SetDueDateDialog.dateSingleLabel: TextView get() =
    dialog!!.findViewById(R.id.date_single_label)
