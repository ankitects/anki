// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.content.Context
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.common.annotations.NeedsTest
import timber.log.Timber

private const val DECK_PICKER_SCREEN_NAME = "DeckPicker"
private const val CARD_BROWSER_SCREEN_NAME = "CardBrowser"
private const val STATISTICS_SCREEN_NAME = "Statistics"
private const val MORE_SCREEN_NAME = "MoreFragment"

/**
 * Controls bottom navigation bar behaviour.
 *
 * DeckPicker (or any activity) can compose this when the bottom navigation
 * developer option is enabled. Fragments are managed using show/hide to
 * preserve state across tab switches.
 *
 * @param activity the host activity (for fragment manager and back press)
 * @param bottomNavigationView the BottomNavigationView from the activity's layout
 * @param fragmentContainerId the resource id of the FragmentContainerView for hosting fragments
 * @param listener callbacks for tab selection events
 */
@NeedsTest("back press returns to Home tab before exiting")
@NeedsTest("tab switches show/hide correct fragments")
class BottomNavController(
    private val activity: FragmentActivity,
    private val bottomNavigationView: BottomNavigationView,
    @IdRes private val fragmentContainerId: Int,
    private val listener: Listener,
) {
    interface Listener {
        /** Show the deck picker content and hide any fragments. */
        fun onHomeSelected()

        /** Show the card browser fragment. */
        fun onBrowserSelected()

        /** Show the statistics fragment. */
        fun onStatsSelected()

        /** Show the "More" destination. */
        fun onMoreSelected()
    }

    enum class NavigationItem(
        @IdRes val id: Int,
        /** Fragment tag used to find and reuse this destination's fragment across tab switches. */
        val tag: String,
        @StringRes val shortcutLabel: Int,
        val analyticsScreenName: String,
    ) {
        HOME(R.id.nav_home, "home", CommonString.deck_picker_group, DECK_PICKER_SCREEN_NAME),
        BROWSER(R.id.nav_browser, "browser", CommonString.card_browser_context_menu, CARD_BROWSER_SCREEN_NAME),
        STATS(R.id.nav_stats, "stats", CommonString.open_statistics, STATISTICS_SCREEN_NAME),
        MORE(R.id.nav_more, "more", CommonString.bottom_nav_more, MORE_SCREEN_NAME),
        ;

        fun title(context: Context): String =
            when (this) {
                HOME -> TR.actionsDecks()
                BROWSER -> context.getString(CommonString.bottom_nav_browse)
                STATS -> TR.statisticsTitle()
                MORE -> context.getString(CommonString.bottom_nav_more)
            }

        companion object {
            fun populateMenu(
                bottomNavigationView: BottomNavigationView,
                context: Context,
            ) {
                entries.forEach { item ->
                    bottomNavigationView.menu.findItem(item.id)?.title = item.title(context)
                }
            }

            fun fromId(
                @IdRes id: Int,
            ): NavigationItem? =
                when (id) {
                    R.id.nav_home -> HOME
                    R.id.nav_browser -> BROWSER
                    R.id.nav_stats -> STATS
                    R.id.nav_more -> MORE
                    else -> null
                }
        }
    }

    private var currentNavigationItem: NavigationItem = NavigationItem.HOME

    /**
     * Handles back press by navigating to the Home tab first.
     *
     * isEnabled is managed externally via switchTo: enabled when on a
     * non-Home tab, disabled when on Home.
     */
    private val backCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                bottomNavigationView.selectedItemId = R.id.nav_home
            }
        }

    /**
     * Wires the tab selection listener and back press handling.
     * Call once after the activity's content view is set up.
     */
    fun setup() {
        NavigationItem.populateMenu(bottomNavigationView, activity)
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    switchTo(NavigationItem.HOME)
                    true
                }
                R.id.nav_browser -> {
                    switchTo(NavigationItem.BROWSER)
                    true
                }
                R.id.nav_stats -> {
                    switchTo(NavigationItem.STATS)
                    true
                }
                R.id.nav_more -> {
                    listener.onMoreSelected()
                    false
                }
                else -> false
            }
        }
    }

    /**
     * Switches to the given navigation item, updating back callback state
     * and notifying the listener. No-op if already on that item.
     */
    private fun switchTo(navItem: NavigationItem) {
        if (navItem == currentNavigationItem) return
        Timber.i("Bottom nav switching to %s", navItem.name)
        currentNavigationItem = navItem
        backCallback.isEnabled = navItem != NavigationItem.HOME
        when (navItem) {
            NavigationItem.HOME -> listener.onHomeSelected()
            NavigationItem.BROWSER -> listener.onBrowserSelected()
            NavigationItem.STATS -> listener.onStatsSelected()
            NavigationItem.MORE -> {} // handled directly in setup(), tab is not selected
        }
    }

    /**
     * Shows a fragment in the container using show/hide.
     * Fragments are added on first visit, then kept alive and reused.
     */
    fun showFragment(
        fragment: Fragment,
        tag: String,
    ) {
        activity.supportFragmentManager.commit {
            activity.supportFragmentManager.fragments.forEach { existing ->
                if (existing.id == fragmentContainerId) {
                    hide(existing)
                }
            }
            val existing = activity.supportFragmentManager.findFragmentByTag(tag)
            if (existing != null) {
                show(existing)
            } else {
                add(fragmentContainerId, fragment, tag)
            }
        }
        activity.findViewById<View>(fragmentContainerId).isVisible = true
    }

    /**
     * Hides all fragments in the container and collapses it.
     * Called by Listener.onHomeSelected to reveal the deck picker content underneath.
     */
    fun hideAllFragments() {
        activity.supportFragmentManager.commit {
            activity.supportFragmentManager.fragments.forEach { existing ->
                if (existing.id == fragmentContainerId) {
                    hide(existing)
                }
            }
        }
        activity.findViewById<View>(fragmentContainerId).isVisible = false
    }
}
