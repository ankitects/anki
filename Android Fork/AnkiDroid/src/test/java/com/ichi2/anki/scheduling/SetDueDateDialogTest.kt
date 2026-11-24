// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.scheduling

import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.launchFragment
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.sched.SetDueDateDays
import com.ichi2.anki.scheduling.SetDueDateViewModel.Tab
import com.ichi2.anki.servicelayer.getFSRSStatus
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.anki.utils.ext.requireParcelable
import com.ichi2.utils.positiveButton
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowToast
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.ichi2.anki.common.android.R as CommonR

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

    @Test
    fun `card ids are readable after recreation`() =
        runTest {
            val cardIds = List(2) { addBasicNote().firstCard().id }
            launchFragment<SetDueDateDialog>(
                themeResId = CommonR.style.Theme_AnkiDroid,
                fragmentArgs = setDueDateArgs(cardIds),
            ).use { scenario ->
                advanceRobolectricLooper()
                scenario.recreate()
                advanceRobolectricLooper()
                scenario.onFragment { fragment ->
                    assertThat(fragment.cardIds, equalTo(cardIds))
                }
            }
        }

    @Test
    fun `ids file is removed after the dialog is dismissed`() =
        runTest {
            val cardIds = List(2) { addBasicNote().firstCard().id }
            val args = setDueDateArgs(cardIds)
            val idsFile = args.requireParcelable<IdsFile>(SetDueDateDialog.ARG_IDS_FILE)

            launchFragment<SetDueDateDialog>(
                themeResId = CommonR.style.Theme_AnkiDroid,
                fragmentArgs = args,
            ).use { scenario ->
                advanceRobolectricLooper()
                assertThat("kept while the dialog is open", idsFile.exists(), equalTo(true))
                scenario.onFragment { it.dismiss() }
                advanceRobolectricLooper()
            }

            assertThat("removed once dismissed", idsFile.exists(), equalTo(false))
        }

    @Test
    fun `missing selection dismisses due date dialog`() =
        withActivity { activity ->
            val card = addBasicNote().firstCard()
            val originalDue = card.due
            val args = setDueDateArgs(listOf(card.id))
            assertTrue(args.requireParcelable<IdsFile>(SetDueDateDialog.ARG_IDS_FILE).delete())
            val fragment = SetDueDateDialog().apply { arguments = args }
            fragment.show(activity.supportFragmentManager, "due-date")
            advanceRobolectricLooper()
            assertNull(activity.supportFragmentManager.findFragmentByTag("due-date"))
            assertThat(col.getCard(card.id).due, equalTo(originalDue))
            assertThat(
                ShadowToast.getTextOfLatestToast(),
                equalTo(targetContext.getString(CommonString.something_wrong)),
            )
        }

    @Test
    fun `unreadable ids file does not crash`() =
        runTest {
            val cardIds = List(2) { addBasicNote().firstCard().id }
            val args = setDueDateArgs(cardIds)
            args.requireParcelable<IdsFile>(SetDueDateDialog.ARG_IDS_FILE).writeBytes(ByteArray(0))

            launchFragment<SetDueDateDialog>(
                themeResId = CommonR.style.Theme_AnkiDroid,
                fragmentArgs = args,
            ).use {
                advanceRobolectricLooper()
            }
        }

    @Test
    fun `loading FSRS forces the interval to match the due date`() = assertSchedulerLoading(fsrsEnabled = true)

    @Test
    fun `loading SM-2 allows changing the interval`() = assertSchedulerLoading(fsrsEnabled = false)

    @Test
    fun `unavailable scheduler setting falls back to SM-2`() = assertSchedulerLoading(fsrsEnabled = null)

    @Test
    fun `show and duplicate requests return without waiting for loading`() =
        withActivity { activity ->
            val firstCardIds = listOf(addBasicNote().firstCard().id)
            val secondCardIds = listOf(addBasicNote().firstCard().id)
            withPausedLoading { finishLoading ->
                SetDueDateDialog.show(activity, firstCardIds)
                SetDueDateDialog.show(activity, secondCardIds)
                advanceRobolectricLooper()

                assertEquals(firstCardIds, assertNotNull(activity.currentDialog).cardIds)
                assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
                assertEquals(1, activity.dueDateFiles.size)
                finishLoading.complete(Unit)
            }
        }

    @Test
    fun `dialog can reopen after dismissal`() =
        withActivity { activity ->
            val firstCardIds = listOf(addBasicNote().firstCard().id)
            val secondCardIds = listOf(addBasicNote().firstCard().id)
            SetDueDateDialog.show(activity, firstCardIds)
            advanceRobolectricLooper()
            val dialog = assertNotNull(activity.currentDialog)

            dialog.dismiss()
            advanceRobolectricLooper()
            assertNull(activity.currentDialog)

            SetDueDateDialog.show(activity, secondCardIds)
            advanceRobolectricLooper()
            val reopenedDialog = assertNotNull(activity.currentDialog)
            assertNotSame(dialog, reopenedDialog)
            assertEquals(secondCardIds, reopenedDialog.cardIds)
            assertTrue(reopenedDialog.requireDialog().isShowing)
        }

    @Test
    fun `dialog can be dismissed and reopened while loading`() =
        withActivity { activity ->
            val firstCardIds = listOf(addBasicNote().firstCard().id)
            val secondCardIds = listOf(addBasicNote().firstCard().id)
            withPausedLoading(fsrsEnabled = true) { finishLoading ->
                SetDueDateDialog.show(activity, firstCardIds)
                advanceRobolectricLooper()
                assertNotNull(activity.currentDialog).dismiss()
                advanceRobolectricLooper()
                assertNull(activity.currentDialog)
                assertTrue(activity.dueDateFiles.isEmpty())

                coEvery { getFSRSStatus() } returns false
                SetDueDateDialog.show(activity, secondCardIds)
                finishLoading.complete(Unit)
                advanceUntilIdle()
                advanceRobolectricLooper()

                val dialog = assertNotNull(activity.currentDialog)
                assertEquals(secondCardIds, dialog.cardIds)
                assertTrue(dialog.requireDialog().isShowing)
                assertTrue(dialog.changeInterval.isVisible)
                assertFalse(dialog.viewModel.fsrsEnabled.value!!)
            }
        }

    private fun assertSchedulerLoading(fsrsEnabled: Boolean?) =
        withActivity { activity ->
            val cardIds = listOf(addBasicNote().firstCard().id)
            withPausedLoading(fsrsEnabled) { finishLoading ->
                SetDueDateDialog.show(activity, cardIds)
                advanceRobolectricLooper()
                val dialog = assertNotNull(activity.currentDialog)
                dialog.singleDayText.setText("3")
                assertFalse(dialog.positiveButtonIsEnabled, "cannot save before the scheduler setting is loaded")
                assertFalse(dialog.changeInterval.isVisible)
                assertNull(dialog.viewModel.updateDueDateAsync().await(), "keyboard submission cannot save while loading")

                finishLoading.complete(Unit)
                advanceUntilIdle()
                advanceRobolectricLooper()

                assertTrue(dialog.positiveButtonIsEnabled)
                assertEquals(fsrsEnabled != true, dialog.changeInterval.isVisible)
                assertEquals(fsrsEnabled == true, dialog.changeInterval.isChecked)
                assertEquals(SetDueDateDays(if (fsrsEnabled == true) "3!" else "3"), dialog.viewModel.calculateDaysParameter())
            }
        }

    private val FragmentActivity.currentDialog: SetDueDateDialog?
        get() = supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG) as SetDueDateDialog?

    private val FragmentActivity.dueDateFiles: List<File>
        get() =
            IdsFile
                .getDirectory()
                .listFiles { _, name -> name.startsWith("set-due-date") }
                .orEmpty()
                .toList()

    private fun withActivity(block: suspend TestScope.(FragmentActivity) -> Unit) =
        runTest {
            Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
                controller.get().setTheme(CommonR.style.Theme_AnkiDroid)
                block(controller.setup().get())
            }
        }

    private suspend fun withPausedLoading(
        fsrsEnabled: Boolean? = false,
        block: suspend (CompletableDeferred<Unit>) -> Unit,
    ) {
        val finishLoading = CompletableDeferred<Unit>()
        mockkStatic(::getFSRSStatus)
        try {
            coEvery { getFSRSStatus() } coAnswers {
                finishLoading.await()
                fsrsEnabled
            }
            block(finishLoading)
        } finally {
            unmockkStatic(::getFSRSStatus)
        }
    }

    private fun setDueDateArgs(cardIds: List<CardId>): Bundle =
        SetDueDateDialog
            .newInstance(targetContext.externalCacheDir ?: targetContext.cacheDir, cardIds)
            .requireArguments()

    private fun testDialog(
        cardCount: Int = 1,
        action: SetDueDateDialog.() -> Unit,
    ) = runTest {
        val cardIds = List(cardCount) { addBasicNote().firstCard().id }
        val dialog = SetDueDateDialog.newInstance(targetContext.externalCacheDir ?: targetContext.cacheDir, cardIds)
        launchFragment(
            themeResId = CommonR.style.Theme_AnkiDroid,
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
