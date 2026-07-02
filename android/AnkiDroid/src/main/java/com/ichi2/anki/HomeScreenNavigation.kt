// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.browser.CardBrowserFragment
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.pages.Statistics
import com.ichi2.anki.settings.Prefs

/**
 * Manages the bottom navigation bar for the home screen.
 *
 * On tablets (fragmented), this is a no-op because tablets use the navigation
 * drawer with a split pane.
 */
@NeedsTest("tab switches show/hide correct fragments")
@NeedsTest("back press returns to Home tab before exiting")
context(deckPicker: DeckPicker)
fun setupBottomNavigation() {
    if (!Prefs.devBottomNavEnabled || deckPicker.fragmented) return

    val bottomNav = deckPicker.findViewById<BottomNavigationView>(R.id.bottom_navigation)
    val fragmentContainer = deckPicker.findViewById<View>(R.id.bottom_nav_fragment_container)
    val contentWrapper = deckPicker.findViewById<View>(R.id.deck_picker_content_wrapper)
    bottomNav.isVisible = true

    // Return to Home tab on back press when on a non-Home tab
    val bottomNavBackCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                bottomNav.selectedItemId = NavigationItem.HOME.id
            }
        }
    deckPicker.onBackPressedDispatcher.addCallback(deckPicker, bottomNavBackCallback)

    // Handle system navigation bar insets for the bottom nav
    ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
        val navBars = insets.getInsets(navigationBars())
        view.updatePadding(bottom = navBars.bottom)
        insets
    }

    bottomNav.setOnItemSelectedListener { item ->
        val navItem = NavigationItem.fromId(item.itemId) ?: return@setOnItemSelectedListener false
        handleNavigationItemSelected(navItem, contentWrapper, fragmentContainer, bottomNavBackCallback)
    }
}

context(deckPicker: DeckPicker)
private fun handleNavigationItemSelected(
    item: NavigationItem,
    contentWrapper: View,
    fragmentContainer: View,
    backCallback: OnBackPressedCallback,
): Boolean =
    when (item) {
        NavigationItem.HOME -> {
            fragmentContainer.isVisible = false
            deckPicker.supportFragmentManager.commit { hideBottomNavFragments() }
            contentWrapper.isVisible = true
            deckPicker.floatingActionMenu.showFloatingActionButton()
            backCallback.isEnabled = false
            true
        }
        NavigationItem.BROWSER -> {
            backCallback.isEnabled = true
            ensureBrowserViewModel()
            showBottomNavFragment(::CardBrowserFragment, item.tag, contentWrapper, fragmentContainer)
            true
        }
        NavigationItem.STATS -> {
            backCallback.isEnabled = true
            showBottomNavFragment(
                {
                    Statistics().apply {
                        arguments = Bundle().apply { putBoolean(Statistics.ARG_HIDE_BACK_BUTTON, true) }
                    }
                },
                item.tag,
                contentWrapper,
                fragmentContainer,
            )
            true
        }
        NavigationItem.MORE -> {
            backCallback.isEnabled = true
            showBottomNavFragment(::MoreFragment, item.tag, contentWrapper, fragmentContainer)
            true
        }
    }

/** Create CardBrowserViewModel before the fragment accesses it via activityViewModels() */
context(deckPicker: DeckPicker)
private fun ensureBrowserViewModel() {
    ViewModelProvider(
        deckPicker.viewModelStore,
        CardBrowserViewModel.factory(
            lastDeckIdRepository = AnkiDroidApp.instance.sharedPrefsLastDeckIdRepository,
            cacheDir = deckPicker.cacheDir,
            options = null,
            isFragmented = false,
        ),
        deckPicker.defaultViewModelCreationExtras,
    )[CardBrowserViewModel::class.java]
}

context(deckPicker: DeckPicker)
private fun showBottomNavFragment(
    newFragment: () -> Fragment,
    tag: String,
    contentWrapper: View,
    fragmentContainer: View,
) {
    contentWrapper.isVisible = false
    deckPicker.floatingActionMenu.hideFloatingActionButton()
    val bottomNav = deckPicker.findViewById<View>(R.id.bottom_navigation)
    (fragmentContainer.layoutParams as? CoordinatorLayout.LayoutParams)?.let { lp ->
        lp.topMargin = 0
        lp.bottomMargin = bottomNav.height
        fragmentContainer.layoutParams = lp
    }
    deckPicker.supportFragmentManager.commit {
        hideBottomNavFragments()
        val existing = deckPicker.supportFragmentManager.findFragmentByTag(tag)
        if (existing != null) {
            show(existing)
        } else {
            add(R.id.bottom_nav_fragment_container, newFragment(), tag)
        }
    }
    fragmentContainer.isVisible = true
}

/** Hides any fragments currently hosted in the bottom-nav container. */
context(deckPicker: DeckPicker)
private fun FragmentTransaction.hideBottomNavFragments() {
    deckPicker.supportFragmentManager.fragments.forEach { fragment ->
        if (fragment.id == R.id.bottom_nav_fragment_container) hide(fragment)
    }
}
