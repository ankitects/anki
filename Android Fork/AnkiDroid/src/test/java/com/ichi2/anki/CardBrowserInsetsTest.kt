// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.view.View
import android.view.View.MeasureSpec
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.ui.RecyclerFastScroller
import com.ichi2.testutils.dispatchInsets
import com.ichi2.testutils.lastItemView
import com.ichi2.testutils.scrollToLastPosition
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.lessThan
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Edge-to-edge inset handling for [CardBrowser].
 *
 * The list draws edge-to-edge under the navigation bar and the fast
 * scroller's track is full-height/edge-to-edge. Only the handle is held inside the safe area
 * (clearing the navigation bar and rounded display corners), so at full scroll the handle's bottom
 * lines up with the last card's resting position.
 */
@RunWith(AndroidJUnit4::class)
class CardBrowserInsetsTest : RobolectricTest() {
    @Test
    fun `track is full-height but the handle is inset to the last card's resting line`() =
        withCardBrowser(noteCount = 1) { browser ->
            val navBarBottom = 48.dp.toPx(targetContext)
            browser.dispatchInsets(navBarBottom = 48.dp)
            browser.layoutForTest()

            // `clipToPadding=false` lets content scroll under the bar, but the bottom padding is the
            // resting position of the last card: a navigation bar's height above the parent's bottom
            assertThat(
                "last card rests a navigation bar's height above the parent's bottom",
                browser.cardList.restingContentBottomToParent,
                equalTo(navBarBottom),
            )
            // the track stays full-height/edge-to-edge: flush with the parent bottom
            assertThat(
                "fast scroller track spans to the parent's bottom",
                browser.fastScroller.distanceToParentBottom,
                equalTo(0),
            )
            //  but the handle is inset to the same line the last card rests on
            assertThat(
                "fast scroller handle is inset to the last card's resting line",
                browser.fastScroller.handleBottomInset,
                equalTo(browser.cardList.restingContentBottomToParent),
            )
        }

    @Test
    fun `track is full-height while scrollable, then trimmed to the inset at the bottom`() =
        withCardBrowser(noteCount = 50) { browser ->
            val navBarBottom = 48.dp.toPx(targetContext)
            browser.dispatchInsets(navBarBottom = 48.dp)
            browser.layoutForTest()

            val scroller = browser.fastScroller
            val track = scroller.bar
            // the list must be scrollable for this scenario to be meaningful
            assertThat("list is scrollable", browser.cardList.canScrollVertically(1), equalTo(true))

            // not at the bottom: the track spans the full edge-to-edge height
            assertThat(
                "track is full-height while there is more to scroll",
                track.bottom,
                equalTo(scroller.height),
            )

            // scroll to the end and re-lay-out so the fast scroller re-positions
            browser.cardList.scrollToLastPosition()
            browser.layoutForTest()

            // at the bottom: the track is trimmed by the inset, aligning with the handle & last row
            assertThat("list is fully scrolled", browser.cardList.canScrollVertically(1), equalTo(false))
            assertThat(
                "track is trimmed to the handle inset once fully scrolled",
                track.bottom,
                equalTo(scroller.height - navBarBottom),
            )
        }

    @Test
    fun `track bottom follows the last row's bottom once it is on screen`() =
        withCardBrowser(noteCount = 50) { browser ->
            val navBarBottom = 48.dp.toPx(targetContext)
            browser.dispatchInsets(navBarBottom = 48.dp)
            browser.layoutForTest()

            val scroller = browser.fastScroller
            val track = scroller.bar
            val list = browser.cardList

            // scroll to the end, then back up by part of the inset so the last row's bottom sits
            // between its resting line and the bottom of the screen
            list.scrollToLastPosition()
            browser.layoutForTest()
            list.scrollBy(0, -navBarBottom / 2)
            browser.layoutForTest()

            val lastRowBottom = list.layoutManager!!.getDecoratedBottom(list.lastItemView)
            assertThat(
                "the last row's bottom is on screen, below its resting line",
                lastRowBottom,
                allOf(greaterThan(scroller.height - navBarBottom), lessThan(scroller.height)),
            )
            assertThat("the track's bottom matches the last row's bottom", track.bottom, equalTo(lastRowBottom))
        }

    @Test
    fun `handle, track and last row rest on the same line when fully scrolled`() =
        withCardBrowser(noteCount = 50) { browser ->
            val navBarBottom = 48.dp.toPx(targetContext)
            browser.dispatchInsets(navBarBottom = 48.dp)
            browser.layoutForTest()

            val scroller = browser.fastScroller
            val track = scroller.bar
            val handle = scroller.handle
            val list = browser.cardList

            list.scrollToLastPosition()
            browser.layoutForTest()
            assertThat("list is fully scrolled", list.canScrollVertically(1), equalTo(false))

            val lastRowBottom = list.layoutManager!!.getDecoratedBottom(list.lastItemView)
            val restingLine = scroller.height - navBarBottom
            assertThat("the last row rests above the safe area", lastRowBottom, equalTo(restingLine))
            assertThat("the track's bottom rests on the same line", track.bottom, equalTo(restingLine))
            assertThat("the handle's bottom rests on the same line", handle.bottom, equalTo(restingLine))
        }

    @Test
    fun `rounded display corners are cleared when larger than the navigation bar`() =
        withCardBrowser(noteCount = 1) { browser ->
            val cornerRadius = 48.dp.toPx(targetContext)
            browser.dispatchInsets(navBarBottom = 24.dp, bottomCornerRadius = 48.dp)
            browser.layoutForTest()

            assertThat(
                "the last card rests above the rounded corners",
                browser.cardList.restingContentBottomToParent,
                equalTo(cornerRadius),
            )
            assertThat(
                "the handle is inset to the last card's resting line",
                browser.fastScroller.handleBottomInset,
                equalTo(cornerRadius),
            )
        }

    @Test
    fun `toolbar is padded past a side navigation bar and cutout`() =
        withCardBrowser(noteCount = 1) { browser ->
            // landscape with 3-button navigation: the navigation bar is a side inset and the
            // camera cutout is on the opposite side
            browser.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)
            browser.layoutForTest()

            val toolbar = browser.findViewById<View>(R.id.toolbar_container)
            assertThat(
                "toolbar is padded past the cutout",
                toolbar.paddingLeft,
                equalTo(32.dp.toPx(targetContext)),
            )
            assertThat(
                "toolbar is padded past the side navigation bar",
                toolbar.paddingRight,
                equalTo(48.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `a side navigation bar clearing the corner removes the bottom buffer`() =
        withCardBrowser(noteCount = 50) { browser ->
            // landscape with 3-button navigation: the bar is wider than the corner radius, so the
            // scroll handle is already inboard of the corner arc and no vertical buffer is needed
            browser.dispatchInsets(navBarRight = 48.dp, bottomCornerRadius = 34.dp)
            browser.layoutForTest()

            val fragmentRoot =
                browser.supportFragmentManager
                    .findFragmentById(R.id.card_browser_frame)!!
                    .requireView()
            assertThat(
                "content is padded past the side navigation bar",
                fragmentRoot.paddingRight,
                equalTo(48.dp.toPx(targetContext)),
            )
            assertThat("no bottom buffer is reserved", browser.cardList.paddingBottom, equalTo(0))
            assertThat("the handle may reach the parent's bottom", browser.fastScroller.handleBottomInset, equalTo(0))

            browser.cardList.scrollToLastPosition()
            browser.layoutForTest()
            assertThat("list is fully scrolled", browser.cardList.canScrollVertically(1), equalTo(false))
            assertThat(
                "the track reaches the parent's bottom when fully scrolled",
                browser.fastScroller.bar.bottom,
                equalTo(browser.fastScroller.height),
            )
        }

    @Test
    fun `a partial side inset reduces the corner clearance by its width`() =
        withCardBrowser(noteCount = 1) { browser ->
            // a side inset narrower than the corner radius leaves the scroll handle inside the
            // corner's horizontal extent, but part-way in: only the remainder of the radius is
            // reserved (48dp radius - 24dp side inset)
            browser.dispatchInsets(navBarRight = 24.dp, bottomCornerRadius = 48.dp)
            browser.layoutForTest()

            assertThat(
                "the clearance is the corner radius less the side inset",
                browser.fastScroller.handleBottomInset,
                equalTo(24.dp.toPx(targetContext)),
            )
        }

    /** The gap, in pixels, between the bottom of this view and the bottom of its parent. */
    private val View.distanceToParentBottom: Int
        get() = (parent as View).height - bottom

    /**
     * The gap, in pixels, between the parent's bottom and where the last list item comes to rest.
     * The list fills its parent, so with `clipToPadding=false` this is just the bottom padding.
     */
    private val RecyclerView.restingContentBottomToParent: Int
        get() = distanceToParentBottom + paddingBottom

    private val CardBrowser.cardList: RecyclerView
        get() = findViewById(R.id.card_browser_list)

    private val CardBrowser.fastScroller: RecyclerFastScroller
        get() = findViewById(R.id.browser_scroller)

    /** Forces a measure/layout pass so view bounds (not just padding/insets) can be asserted. */
    private fun CardBrowser.layoutForTest() {
        val content = findViewById<View>(android.R.id.content)
        val width = 1080
        val height = 2400
        content.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        content.layout(0, 0, width, height)
    }
}
