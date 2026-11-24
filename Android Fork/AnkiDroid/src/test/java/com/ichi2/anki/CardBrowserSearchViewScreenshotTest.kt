// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.widget.HorizontalScrollView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.filters.SdkSuppress
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.search.SearchView
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.android.view.locationInWindow
import com.ichi2.anki.browser.CardBrowserViewModel.ChangeMultiSelectMode.SingleSelectCause
import com.ichi2.anki.browser.withCardBrowserFragment
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.ui.RecyclerFastScroller
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.scrollToEnd
import com.ichi2.testutils.simulateKeyboard
import com.ichi2.testutils.simulateSystemBars
import com.ichi2.testutils.windowInsetsOf
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CardBrowserSearchViewScreenshotTest : ScreenshotTest() {
    @Test
    fun previewIconDuringSelection(
        @TestParameter rtl: Boolean,
    ) {
        if (rtl) RuntimeEnvironment.setQualifiers("+ldrtl")
        repeat(3) { addBasicNote("Card $it", "Answer $it") }
        withCardBrowserFragment(useSearchView = true) {
            (requireActivity() as CardBrowser).waitForSearchResults()
            advanceRobolectricLooper()
            val direction = if (rtl) "rtl" else "ltr"

            fun capturePreview(state: String) {
                val preview = requireNotNull(searchBar).findViewById<View>(R.id.action_preview_many)
                assertTrue(preview?.isShown == true, "Preview must be visible in the new search bar")
                captureScreen("${direction}_preview_$state")
            }

            capturePreview("before_selection")
            val row = requireNotNull(cardsListView.findViewHolderForAdapterPosition(0)).itemView
            assertTrue(row.performLongClick())
            advanceRobolectricLooper()
            assertTrue(activityViewModel.isInMultiSelectMode)
            capturePreview("selected")

            activityViewModel.endMultiSelectMode(SingleSelectCause.NavigateBack)
            advanceRobolectricLooper()
            capturePreview("after_selection")
        }
    }

    @Test
    fun statusBarWhenScrolling() {
        repeat(50) { addBasicNote("Card $it", "Answer $it") }
        withCardBrowserFragment(useSearchView = true) {
            val browser = requireActivity() as CardBrowser
            browser.waitForSearchResults()
            // Avoid simulateSystemBars' tinted bands so the app's status-bar background stays visible.
            browser.dispatchInsets(navBarBottom = 24.dp)
            advanceRobolectricLooper()
            val root = requireView()
            val appBar = root.findViewById<AppBarLayout>(R.id.browser_app_bar)
            val coordinator = appBar.parent as CoordinatorLayout
            val behavior = (appBar.layoutParams as CoordinatorLayout.LayoutParams).behavior as AppBarLayout.Behavior
            val list = root.findViewById<RecyclerView>(R.id.card_browser_list)
            val searchBar = root.findViewById<View>(R.id.search_bar)
            val expandedSearchTop = searchBar.locationInWindow().y
            val listScroll = 80.dp.toPx(targetContext)
            val distance = appBar.totalScrollRange + listScroll

            fun scroll(dy: Int) {
                val consumed = IntArray(2)
                list.dispatchNestedPreScroll(0, dy, consumed, null, ViewCompat.TYPE_TOUCH)
                list.scrollBy(0, dy - consumed[1])
                advanceRobolectricLooper()
            }

            // Keep one gesture active through the reversal: scrolling rows must not disconnect the toolbar.
            assertTrue(list.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH))
            scroll(distance)
            assertTrue(list.canScrollVertically(-1))
            assertEquals(0, list.locationInWindow().y, "collapsed results fill the status-bar area")
            captureScreen("toolbar_return_scrolled")

            scroll(-distance)
            list.stopNestedScroll(ViewCompat.TYPE_TOUCH)
            advanceRobolectricLooper()
            assertTrue(!list.canScrollVertically(-1))
            assertEquals(expandedSearchTop, searchBar.locationInWindow().y, "returning to the top restores the toolbar")
            assertTrue(!appBar.isLifted)
            captureScreen("toolbar_return_at_top")

            // Reveal the app bar over scrolled results: its status-bar background must match its lifted color.
            list.scrollBy(0, listScroll)
            behavior.onStopNestedScroll(coordinator, appBar, list, ViewCompat.TYPE_TOUCH)
            advanceRobolectricLooper()
            assertTrue(appBar.isLifted)
            captureScreen("status_color_lifted")

            val middleOfStatusBar = 12.dp.toPx(targetContext)
            val chips = root.findViewById<View>(R.id.chip_group)
            assertTrue(list.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH))
            scroll(chips.locationInWindow().y + chips.height / 2 - middleOfStatusBar)
            captureScreen("status_color_controls")

            // The last toolbar edge remains faded while results beneath it are fully visible.
            scroll(list.locationInWindow().y - middleOfStatusBar)
            assertEquals(middleOfStatusBar, list.locationInWindow().y)
            captureScreen("status_color_toolbar_leaving")
            list.stopNestedScroll(ViewCompat.TYPE_TOUCH)
        }
    }

    @Test
    fun shortResultsKeepHeadingsVisible() {
        repeat(50) { addBasicNote(if (it < 13) "Keep $it" else "Other $it", "Answer $it") }
        withCardBrowserFragment(useSearchView = true) {
            val browser = requireActivity() as CardBrowser
            browser.waitForSearchResults()
            browser.dispatchInsets(navBarBottom = 24.dp)
            advanceRobolectricLooper()
            val appBar = requireView().findViewById<AppBarLayout>(R.id.browser_app_bar)
            val list = requireView().findViewById<RecyclerView>(R.id.card_browser_list)
            val headings = requireView().findViewById<View>(R.id.browser_column_headings)
            val expandedHeadingTop = headings.locationInWindow().y
            appBar.setExpanded(false, false)
            advanceRobolectricLooper()
            assertTrue(headings.locationInWindow().y < expandedHeadingTop)

            activityViewModel.setQuery("Keep").join()
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            assertEquals(13, cardsAdapter.itemCount)
            assertTrue(!list.canScrollVertically(1))
            assertEquals(expandedHeadingTop, headings.locationInWindow().y, "filtering to a short list restores the headings")

            // Further upward dragging must keep the headings visible when every result fits.
            list.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
            list.dispatchNestedPreScroll(0, appBar.totalScrollRange, IntArray(2), null, ViewCompat.TYPE_TOUCH)
            list.stopNestedScroll(ViewCompat.TYPE_TOUCH)
            advanceRobolectricLooper()
            assertEquals(expandedHeadingTop, headings.locationInWindow().y, "short results keep their headings visible")
            captureScreen("short_results_after_filtering")
        }
    }

    @Test
    fun lastResultBelowExpandedToolbar() {
        // These rows fit in the measured list, but extend below the screen with the toolbar expanded.
        repeat(36) { addBasicNote("Card ${it.toString().padStart(2, '0')}", "Answer $it") }
        withCardBrowserFragment(useSearchView = true) {
            val browser = requireActivity() as CardBrowser
            browser.waitForSearchResults()
            browser.dispatchInsets(navBarBottom = 24.dp)
            advanceRobolectricLooper()
            val root = requireView()
            val appBar = root.findViewById<AppBarLayout>(R.id.browser_app_bar)
            val list = cardsListView

            list.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH)
            list.dispatchNestedPreScroll(0, appBar.totalScrollRange, IntArray(2), null, ViewCompat.TYPE_TOUCH)
            list.scrollToEnd()
            list.stopNestedScroll(ViewCompat.TYPE_TOUCH)
            advanceRobolectricLooper()
            captureScreen("last_result_after_scrolling")

            val lastRow = requireNotNull(layoutManager.findViewByPosition(cardsAdapter.itemCount - 1))
            assertTrue(
                lastRow.locationInWindow().y + lastRow.height <= root.locationInWindow().y + root.height - list.paddingBottom,
                "scrolling reveals the entire last result above the navigation bar",
            )
        }
    }

    @Test
    fun filterChips(
        @TestParameter rtl: Boolean,
    ) {
        if (rtl) RuntimeEnvironment.setQualifiers("+ldrtl")
        withCardBrowserFragment(useSearchView = true) {
            advanceRobolectricLooper()
            val scroller = requireView().findViewById<HorizontalScrollView>(R.id.chip_group)
            val direction = if (rtl) "rtl" else "ltr"
            captureScreen("${direction}_chips_start")

            scroller.scrollTo(scroller.getChildAt(0).width, 0)
            scroller.scrollTo(scroller.scrollX / 2, 0)
            captureScreen("${direction}_chips_scrolled")
        }
    }

    @Test
    fun browser(
        @TestParameter landscape: Boolean,
    ) {
        val orientation = setOrientation(landscape)
        repeat(50) { addBasicNote("Card $it", "Answer $it") }
        withCardBrowserFragment(useSearchView = true) {
            val browser = requireActivity() as CardBrowser
            browser.waitForSearchResults()
            browser.simulateSystemBars(navBarBottom = 24.dp, bottomCornerRadius = 34.dp)
            // Match a user scrolling down: the app bar collapses before the list reaches its end.
            requireView().findViewById<AppBarLayout>(R.id.browser_app_bar).setExpanded(false, false)
            advanceRobolectricLooper()
            requireView().findViewById<RecyclerView>(R.id.card_browser_list).scrollToEnd()
            requireView().findViewById<RecyclerFastScroller>(R.id.browser_scroller).show(animate = false)
            advanceRobolectricLooper()
            captureScreen("${orientation}_scrolled_to_bottom")
        }
    }

    @Test
    fun search(
        @TestParameter landscape: Boolean,
        @TestParameter searchTools: Boolean,
    ) {
        val orientation = setOrientation(landscape)
        withCardBrowserFragment(useSearchView = true, useSearchTools = searchTools) {
            val browser = requireActivity() as CardBrowser
            requireView().findViewById<SearchView>(R.id.search_view).show()
            advanceRobolectricLooper()
            if (searchTools) {
                requireView().findViewById<View>(R.id.toggle_advanced_search).performClick()
                advanceRobolectricLooper()
            }
            browser.simulateKeyboard(keyboardHeight = 200.dp)
            with(browser) {
                window.simulateSystemBars(
                    windowInsetsOf(
                        navBarBottom = 48.dp,
                        cutoutLeft = if (landscape) 32.dp else 0.dp,
                        imeBottom = 200.dp,
                    ),
                )
            }
            val mode = if (searchTools) "search_tools" else "search"
            captureScreen("${orientation}_${mode}_keyboard")
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
    fun statusBarMatchesOpenSearch() {
        withCardBrowserFragment(useSearchView = true) {
            val browser = requireActivity() as CardBrowser
            browser.dispatchInsets()
            requireView().findViewById<SearchView>(R.id.search_view).show()
            advanceRobolectricLooper()
            val decor = browser.window.decorView
            val screen = createBitmap(decor.width, decor.height)
            PixelCopy.request(browser.window, screen, {}, Handler(Looper.getMainLooper()))
            advanceRobolectricLooper()
            val statusBarBottom = 24.dp.toPx(targetContext)
            val x = screen.width / 2
            assertEquals(
                "#%08X".format(screen[x, statusBarBottom + 2]),
                "#%08X".format(screen[x, statusBarBottom / 2]),
                "status bar matches the search view",
            )
            captureScreen("status_bar_open_search")
        }
    }
}
