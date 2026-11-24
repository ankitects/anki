// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.navigationBars
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.browser.CardBrowserFragment
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.pages.Statistics
import com.google.android.material.R as MaterialR

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
    if (deckPicker.supportFragmentManager.findFragmentByTag(NavigationItem.BROWSER.tag) != null) {
        ensureBrowserViewModel()
    }
    if (!deckPicker.bottomNavigationEnabled) return

    val binding = deckPicker.binding
    val bottomNav = checkNotNull(binding.bottomNavigation)
    val fragmentContainer = checkNotNull(binding.bottomNavFragmentContainer)
    val studiedSummary = deckPicker.deckPickerBinding.reviewSummaryTextView
    // Once raised above the bar, the summary overlaps the deck list: a solid background stops deck names showing through.
    studiedSummary.setBackgroundColor(MaterialColors.getColor(bottomNav, MaterialR.attr.colorSurface))
    bottomNav.isVisible = true
    val scrollBehavior = HideViewOnScrollBehavior.from(bottomNav)

    fun updateFragmentBottomMargin() {
        val margin =
            if (scrollBehavior.isScrolledIn) {
                bottomNav.height
            } else {
                ViewCompat.getRootWindowInsets(bottomNav)?.getInsets(navigationBars())?.bottom ?: 0
            }
        if (fragmentContainer.marginBottom != margin) {
            fragmentContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = margin }
        }
    }

    fun positionStudiedSummary(animate: Boolean) {
        // The "Studied X cards today" summary is anchored to the bottom of the Decks layout, not to the bar.
        // Raise it above the bar while the bar is shown. When the bar hides, return it to its resting
        // position, where its bottom padding already keeps it above the system navigation bar.
        val translation =
            if (scrollBehavior.isScrolledIn && studiedSummary.height > 0) {
                val navLocation = IntArray(2).also(bottomNav::getLocationInWindow)
                val summaryLocation = IntArray(2).also(studiedSummary::getLocationInWindow)
                // Subtract current translations so this is the resting position, even mid-animation.
                val navTop = navLocation[1] - bottomNav.translationY
                val summaryContentBottom =
                    summaryLocation[1] - studiedSummary.translationY + studiedSummary.height - studiedSummary.paddingBottom
                // Only ever raise it: an open keyboard already pads the text above the bar.
                (navTop - summaryContentBottom - studiedSummary.marginBottom).coerceAtMost(0f)
            } else if (scrollBehavior.isScrolledIn) {
                -bottomNav.height.toFloat()
            } else {
                0f
            }
        // The deck list fades from the summary downwards. Moving the summary does not redraw that fade,
        // so redraw it on every step, or it stays at the summary's old position.
        val fadeWrapper = deckPicker.deckPickerBinding.decksFadeWrapper
        studiedSummary.animate().cancel()
        if (animate) {
            studiedSummary
                .animate()
                .translationY(translation)
                .setDuration(bottomNav.resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
                .setUpdateListener { fadeWrapper.invalidate() }
                .start()
        } else {
            studiedSummary.translationY = translation
            fadeWrapper.invalidate()
        }
    }

    // Never hide the bar while explore-by-touch (e.g. TalkBack) is on, so it stays reachable.
    scrollBehavior.disableOnTouchExploration(true)
    scrollBehavior.addOnScrollStateChangedListener { _, _ ->
        updateFragmentBottomMargin()
        positionStudiedSummary(animate = true)
        // Re-apply insets so the Decks FAB and list padding follow the bar's new position.
        ViewCompat.requestApplyInsets(deckPicker.deckPickerBinding.root)
    }

    NavigationItem.populateMenu(bottomNav, deckPicker)

    deckPicker.onBackPressedDispatcher.addCallback(deckPicker, deckPicker.bottomNavBackCallback)

    // Handle system navigation bar insets for the bottom nav
    ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
        val navBars = insets.getInsets(navigationBars())
        view.updatePadding(bottom = navBars.bottom)
        insets
    }

    bottomNav.setOnItemSelectedListener { item ->
        val navItem = NavigationItem.fromId(item.itemId) ?: return@setOnItemSelectedListener false
        if (item.itemId != bottomNav.selectedItemId) {
            Analytics.sendAnalyticsScreenView(navItem.analyticsScreenName)
            scrollBehavior.slideIn(bottomNav)
        }
        showTab(navItem)
        true
    }

    // A second tap on Browse opens search in the current browser.
    bottomNav.setOnItemReselectedListener { item ->
        if (item.itemId == NavigationItem.BROWSER.id) {
            val browser = deckPicker.supportFragmentManager.findFragmentByTag(NavigationItem.BROWSER.tag) as? CardBrowserFragment
            browser?.openSearch()
        }
    }

    bottomNav.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
        updateFragmentBottomMargin()
        if (bottom - top != oldBottom - oldTop) positionStudiedSummary(animate = false)
    }
    studiedSummary.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (scrollBehavior.isScrolledIn) positionStudiedSummary(animate = false)
    }
}

internal fun BottomNavigationView.selectTab(item: NavigationItem) {
    if (selectedItemId != item.id) {
        selectedItemId = item.id
    }
}

context(deckPicker: DeckPicker)
fun showRestoredBottomNavTab() {
    val item = selectedBottomNavItem() ?: return
    showTab(item)
}

context(deckPicker: DeckPicker)
fun selectedBottomNavItem(): NavigationItem? {
    if (!deckPicker.bottomNavigationEnabled) return null
    val bottomNav = deckPicker.binding.bottomNavigation ?: return null
    return NavigationItem.fromId(bottomNav.selectedItemId)
}

context(deckPicker: DeckPicker)
private fun showTab(item: NavigationItem) {
    val binding = deckPicker.binding
    val contentWrapper = checkNotNull(binding.deckPickerContentWrapper)
    val fragmentContainer = checkNotNull(binding.bottomNavFragmentContainer)
    deckPicker.bottomNavBackCallback.isEnabled = item != NavigationItem.HOME
    when (item) {
        NavigationItem.HOME -> {
            fragmentContainer.isVisible = false
            deckPicker.supportFragmentManager.commit { hideBottomNavFragments() }
            contentWrapper.isVisible = true
            deckPicker.floatingActionMenu.showFloatingActionButton()
        }
        NavigationItem.BROWSER -> {
            ensureBrowserViewModel()
            showBottomNavFragment(::CardBrowserFragment, item.tag, contentWrapper, fragmentContainer)
        }
        NavigationItem.STATS -> {
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
        }
        NavigationItem.MORE -> {
            showBottomNavFragment(::MoreFragment, item.tag, contentWrapper, fragmentContainer)
        }
    }
}

/** Create CardBrowserViewModel before the fragment accesses it via activityViewModels() */
context(deckPicker: DeckPicker)
private fun ensureBrowserViewModel() {
    ViewModelProvider(
        deckPicker.viewModelStore,
        CardBrowserViewModel.factory(
            lastDeckIdRepository = AnkiDroidApp.instance.sharedPrefsLastDeckIdRepository,
            idsFileDirectory = IdsFile.getDirectory(),
            options = null,
            isFragmented = false,
            initialDeckId = null,
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

/**
 * Hides the bottom bar on downward scrolls and shows it on upward scrolls.
 * For views which do not send nested-scroll events to the bar's [HideViewOnScrollBehavior].
 */
fun Fragment.updateBottomNavOnScroll(dy: Int) {
    val deckPicker = activity as? DeckPicker ?: return
    if (dy == 0 || !isVisible || !deckPicker.bottomNavigationEnabled) return
    val bottomNav = deckPicker.binding.bottomNavigation ?: return
    val behavior = HideViewOnScrollBehavior.from(bottomNav)
    if (dy > 0) behavior.slideOut(bottomNav) else behavior.slideIn(bottomNav)
}

/** Hides any fragments currently hosted in the bottom-nav container. */
context(deckPicker: DeckPicker)
private fun FragmentTransaction.hideBottomNavFragments() {
    deckPicker.supportFragmentManager.fragments.forEach { fragment ->
        if (fragment.id == R.id.bottom_nav_fragment_container) hide(fragment)
    }
}
