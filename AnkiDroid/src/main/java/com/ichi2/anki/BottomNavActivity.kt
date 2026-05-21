// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ichi2.anki.databinding.ActivityBottomNavBinding
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

/**
 * Base class that provides the bottom navigation bar
 *
 * Subclasses will extend this instead of the NavigationDrawerActivity
 * when the bottom navigation bar developer option is enabled. The bottom nav
 * replaces the navigation drawer for phone-sized screens.
 *
 * Fragments are managed using show/hide to allow for state persistence.
 */
abstract class BottomNavActivity : AnkiActivity() {
    private val binding by viewBinding(ActivityBottomNavBinding::bind)
    lateinit var bottomNavigationView: BottomNavigationView private set
    private var currentNavigationItem: NavigationItem = NavigationItem.HOME

    enum class NavigationItem {
        HOME,
        BROWSER,
        STATS,
        MORE,
    }

    protected val bottomNavBackCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                if (currentNavigationItem != NavigationItem.HOME) {
                    binding.bottomNavigation.selectedItemId = R.id.nav_home
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

    /**
     * Intializes the bottom navigation bar and wires the tab selection listeners.
     * Called by DeckPicker after it's content view is set up.
     */
    protected fun setupBottomNavigation() {
        onBackPressedDispatcher.addCallback(this, bottomNavBackCallback)
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    switchToNavigationItem(NavigationItem.HOME)
                    true
                }
                R.id.nav_browser -> {
                    switchToNavigationItem(NavigationItem.BROWSER)
                    true
                }
                R.id.nav_stats -> {
                    switchToNavigationItem(NavigationItem.STATS)
                    true
                }
                R.id.nav_more -> {
                    onMoreSelected()
                    false
                }
                else -> false
            }
        }
    }

    private fun switchToNavigationItem(navItem: NavigationItem) {
        if (navItem == currentNavigationItem) return
        Timber.i("Bottom nav switching to %s", navItem.name)
        currentNavigationItem = navItem
        bottomNavBackCallback.isEnabled = navItem != NavigationItem.HOME
        when (navItem) {
            NavigationItem.HOME -> onHomeSelected()
            NavigationItem.BROWSER -> onBrowserSelected()
            NavigationItem.STATS -> onStatsSelected()
            NavigationItem.MORE -> {}
        }
    }

    /** Show the deck picker and hide any fragments. */
    protected abstract fun onHomeSelected()

    /** Show the card browser fragment. */
    protected abstract fun onBrowserSelected()

    /** Show the statistics fragment. */
    protected abstract fun onStatsSelected()

    /** Show the "More" bottom sheet with settings, help, etc. */
    protected abstract fun onMoreSelected()

    /**
     * Shows a fragment in the fragment container using show/hide.
     * Fragments are added on first visit and then reused.
     */
    protected fun showFragment(
        fragment: Fragment,
        tag: String,
    ) {
        binding.bottomNavFragmentContainer.isVisible = true

        val transaction =
            supportFragmentManager.commit {
                supportFragmentManager.fragments.forEach { existing ->
                    if (existing.id == R.id.bottom_nav_fragment_container) {
                        hide(existing)
                    }
                }
                val existing = supportFragmentManager.findFragmentByTag(tag)
                if (existing != null) {
                    show(existing)
                } else {
                    add(R.id.bottom_nav_fragment_container, fragment, tag)
                }
            }
    }
}
