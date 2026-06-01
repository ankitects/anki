// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ichi2.anki.common.annotations.NeedsTest
import timber.log.Timber

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

    enum class NavigationItem {
        HOME,
        BROWSER,
        STATS,
        MORE,
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
