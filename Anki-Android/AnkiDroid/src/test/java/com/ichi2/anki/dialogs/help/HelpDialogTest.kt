// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs.help

import android.os.Bundle
import androidx.fragment.app.testing.launchFragment
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.help.HelpItem.Action.Rate
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class HelpDialogTest {
    private lateinit var mockActionDispatcher: HelpItemActionsDispatcher

    @Before
    fun setUp() {
        mockActionDispatcher = mockk(relaxed = true)
    }

    @Test
    fun `Support menu handles Rate item availability correctly`() {
        // if the system doesn't have an app to handle rate intents, don't show rate menu item
        val itemsWithoutRate = HelpDialog.newSupportInstance(false).requireArgsHelpEntries()
        assertFalse(
            itemsWithoutRate.any { it.action == Rate },
            "Found help menu item for Rate but system can't handle it",
        )
        // if the system has an app to handle rate intents, show rate menu item
        val itemsWithRate = HelpDialog.newSupportInstance(true).requireArgsHelpEntries()
        assertTrue(
            itemsWithRate.any { it.action == Rate },
            "Missing help menu item for Rate when system can handle it",
        )
    }

    @Test
    fun `Help contains the expected items at start`() {
        // checking the support menu
        val expectedSupportItems =
            listOf(
                R.string.help_item_support_opencollective_donate,
                R.string.multimedia_editor_trans_translate,
                R.string.help_item_support_develop_ankidroid,
                R.string.help_item_support_rate_ankidroid,
                R.string.help_item_support_other_ankidroid,
                R.string.send_feedback,
            )
        val actualSupportItems =
            HelpDialog.newSupportInstance(true).requireArgsHelpEntries().map { it.titleResId }
        assertEquals(
            expectedSupportItems,
            actualSupportItems,
            "Unexpected support menu item at start",
        )
        // checking the help menu
        val expectedHelpItems =
            listOf(
                R.string.help_title_using_ankidroid,
                R.string.help_title_get_help,
                R.string.help_title_community,
                R.string.help_title_privacy,
            )
        val actualHelpItems =
            HelpDialog.newHelpInstance().requireArgsHelpEntries().map { it.titleResId }
        assertEquals(
            expectedHelpItems,
            actualHelpItems,
            "Unexpected help menu item at start",
        )
    }

    @Test
    fun `Menu items IDs are consistent`() {
        // support menu items have unique ids
        assertEquals(
            supportMenuItems.size,
            supportMenuItems.map { it.id }.toSet().size,
            "Support menu has items with the same id",
        )
        // main help menu items have unique ids
        assertEquals(
            mainHelpMenuItems.size,
            mainHelpMenuItems.map { it.id }.toSet().size,
            "Main help menu has items with the same id",
        )
        // help menu child items have a non-null valid parent id
        val allFoundParentIds = childHelpMenuItems.map { it.parentId }
        assertFalse(
            allFoundParentIds
                .any { it == null || !mainHelpMenuItems.map { entry -> entry.id }.contains(it) },
            "Help item has an invalid parentId",
        )
    }

    @Test
    fun `Help menu handles submenus correctly`() {
        // simulate a help menu start
        launchFragment<HelpDialog>(
            fragmentArgs =
                Bundle().apply {
                    putInt(HelpDialog.ARG_MENU_TITLE, R.string.help)
                    putParcelableArray(ARG_MENU_ITEMS, mainHelpMenuItems)
                },
            themeResId = R.style.Theme_Light,
            initialState = Lifecycle.State.RESUMED,
        ).onFragment {
            onView(withText(R.string.help_title_community)).inRoot(isDialog()).perform(click())
            // check that the expected six children are shown
            onView(withText(R.string.help_item_discord))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText(R.string.help_item_reddit))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText(R.string.help_item_facebook))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText(R.string.help_item_mailing_list))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText(R.string.help_item_twitter))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText(R.string.help_item_anki_forums))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            // press back
            pressBackUnconditionally()
            // check that the expected initial four menu items are shown
            onView(withText(R.string.help_title_community))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText(R.string.help_title_get_help))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText(R.string.help_title_privacy))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            onView(withText(R.string.help_title_using_ankidroid))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun `Help menu item executes expected action on menu item selection`() {
        // simulate a help menu start
        launchFragment<HelpDialog>(
            fragmentArgs =
                Bundle().apply {
                    putInt(HelpDialog.ARG_MENU_TITLE, R.string.help)
                    putParcelableArray(ARG_MENU_ITEMS, mainHelpMenuItems)
                },
            themeResId = R.style.Theme_Light,
            initialState = Lifecycle.State.RESUMED,
        ).onFragment { fragment ->
            fragment.actionsDispatcher = mockActionDispatcher
            // start the first submenu
            onView(withText(R.string.help_title_using_ankidroid))
                .inRoot(isDialog())
                .perform(click())
            // the manual url is being shown
            onView(withText(R.string.help_item_ankidroid_manual))
                .inRoot(isDialog())
                .perform(click())
            verify(exactly = 1) { mockActionDispatcher.onOpenUrl(AnkiDroidApp.manualUrl) }
            // an url resource is being shown
            onView(withText(R.string.help_item_anki_manual)).inRoot(isDialog()).perform(click())
            verify(exactly = 1) { mockActionDispatcher.onOpenUrlResource(R.string.link_anki_manual) }
            pressBackUnconditionally()
            // start the second submenu
            onView(withText(R.string.help_title_get_help)).inRoot(isDialog()).perform(click())
            // the feedback url is being shown
            onView(withText(R.string.help_item_report_bug)).inRoot(isDialog()).perform(click())
            verify(exactly = 1) { mockActionDispatcher.onOpenUrl(AnkiDroidApp.feedbackUrl) }
            // a report is sent
            onView(withText(R.string.help_title_send_exception))
                .inRoot(isDialog())
                .perform(click())
            verify(exactly = 1) { mockActionDispatcher.onSendReport() }
        }
    }
}
