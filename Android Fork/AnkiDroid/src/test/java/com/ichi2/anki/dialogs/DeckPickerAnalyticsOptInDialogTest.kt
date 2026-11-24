// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.testing.launchFragment
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnkiDroidUsageAnalytics
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.getCheckBoxPrompt
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class DeckPickerAnalyticsOptInDialogTest {
    @Before
    fun setUp() {
        mockkObject(AnkiDroidUsageAnalytics)
        every { AnkiDroidUsageAnalytics.isEnabled = any() } returns Unit
        // the base AnalyticsDialogFragment reports a screen view in onResume
        every { AnkiDroidUsageAnalytics.sendAnalyticsScreenView(any<Any>()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(AnkiDroidUsageAnalytics)
    }

    @Test
    fun `opting in enables analytics for the current session`() {
        showDialog(optIn = true)

        verify(exactly = 1) { AnkiDroidUsageAnalytics.isEnabled = true }
    }

    @Test
    fun `declining disables analytics for the current session`() {
        showDialog(optIn = false)

        verify(exactly = 1) { AnkiDroidUsageAnalytics.isEnabled = false }
    }

    @Test
    fun `cancelling does not change the opt-in state`() {
        launchFragment<DeckPickerAnalyticsOptInDialog>(
            themeResId = CommonR.style.Theme_AnkiDroid,
            initialState = Lifecycle.State.RESUMED,
        ).onFragment { fragment ->
            (fragment.dialog as AlertDialog).cancel()
        }

        verify(exactly = 0) { AnkiDroidUsageAnalytics.isEnabled = any() }
    }

    @Test
    fun `show reuses the current prompt and its selection`() {
        withActivity { activity ->
            DeckPickerAnalyticsOptInDialog.show(activity)
            val original = activity.analyticsFragment()
            original.requireDialog().let { assertIs<AlertDialog>(it).getCheckBoxPrompt().isChecked = true }

            repeat(2) { DeckPickerAnalyticsOptInDialog.show(activity) }

            assertSame(original, activity.analyticsFragment())
            assertTrue(assertIs<AlertDialog>(original.requireDialog()).getCheckBoxPrompt().isChecked)
        }
    }

    @Test
    fun `show reuses a restored prompt while another dialog is open`() {
        Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
            controller.get().setTheme(CommonR.style.Theme_AnkiDroid)
            val activity = controller.setup().get()
            DeckPickerAnalyticsOptInDialog.show(activity)
            assertIs<AlertDialog>(activity.analyticsFragment().requireDialog()).getCheckBoxPrompt().isChecked = true
            activity.showDialogFragment(DialogFragment())

            controller.recreate()
            val restoredActivity = controller.get()
            val restored = restoredActivity.analyticsFragment()
            val manager = restoredActivity.supportFragmentManager
            val otherDialog = assertIs<DialogFragment>(manager.findFragmentByTag(DIALOG_FRAGMENT_TAG))

            repeat(2) { DeckPickerAnalyticsOptInDialog.show(restoredActivity) }

            assertSame(restored, restoredActivity.analyticsFragment())
            assertTrue(assertIs<AlertDialog>(restored.requireDialog()).getCheckBoxPrompt().isChecked)
            assertSame(otherDialog, manager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
            assertTrue(otherDialog.requireDialog().isShowing)
            assertEquals(1, manager.backStackEntryCount)
        }
    }

    @Test
    fun `cancelling the prompt preserves other dialogs`() {
        assertOtherDialogPreserved { it.cancel() }

        verify(exactly = 0) { AnkiDroidUsageAnalytics.isEnabled = any() }
    }

    @Test
    fun `continuing past the prompt preserves other dialogs`() {
        assertOtherDialogPreserved { it.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }

        verify(exactly = 1) { AnkiDroidUsageAnalytics.isEnabled = false }
    }

    private fun assertOtherDialogPreserved(dismissPrompt: (AlertDialog) -> Unit) {
        withActivity { activity ->
            val otherDialog = DialogFragment()
            activity.showDialogFragment(otherDialog)
            DeckPickerAnalyticsOptInDialog.show(activity)

            dismissPrompt(assertIs<AlertDialog>(activity.analyticsFragment().requireDialog()))
            ShadowLooper.idleMainLooper()
            val manager = activity.supportFragmentManager
            manager.executePendingTransactions()

            assertTrue(manager.fragments.none { it is DeckPickerAnalyticsOptInDialog })
            assertSame(otherDialog, manager.findFragmentByTag(DIALOG_FRAGMENT_TAG))
            assertTrue(otherDialog.requireDialog().isShowing)
            assertEquals(1, manager.backStackEntryCount)
        }
    }

    private fun FragmentActivity.analyticsFragment(): DeckPickerAnalyticsOptInDialog =
        supportFragmentManager.fragments.filterIsInstance<DeckPickerAnalyticsOptInDialog>().single()

    private fun withActivity(block: (FragmentActivity) -> Unit) {
        Robolectric.buildActivity(FragmentActivity::class.java).use { controller ->
            controller.get().setTheme(CommonR.style.Theme_AnkiDroid)
            block(controller.setup().get())
        }
    }

    private fun showDialog(optIn: Boolean) {
        launchFragment<DeckPickerAnalyticsOptInDialog>(
            themeResId = CommonR.style.Theme_AnkiDroid,
            initialState = Lifecycle.State.RESUMED,
        )
        // the checkbox starts unticked, so only opting in needs a click
        if (optIn) {
            onView(withId(R.id.checkbox)).inRoot(isDialog()).perform(click())
        }
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
    }
}
