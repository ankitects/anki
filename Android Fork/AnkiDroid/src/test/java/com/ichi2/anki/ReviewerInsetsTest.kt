// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import androidx.annotation.IdRes
import androidx.core.content.edit
import androidx.core.view.marginBottom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.reviewer.FullScreenMode
import com.ichi2.testutils.dispatchInsets
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import com.ichi2.anki.common.android.R as CommonR

/**
 * Edge-to-edge inset handling for the legacy [Reviewer].
 *
 * The app bar's background spans the full width and draws behind the status bar; only its
 * content is inset. The counts bar, card and answer area are held inside the safe area, with
 * the answer area's background extending underneath the navigation bar.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
@RunWith(AndroidJUnit4::class)
class ReviewerInsetsTest : RobolectricTest() {
    // rendering a card requires a media folder
    override fun getCollectionStorageMode() = CollectionStorageMode.IN_MEMORY_WITH_MEDIA

    @Test
    fun `app bar draws behind the status bar, with its content inset`() =
        withReviewer { reviewer ->
            reviewer.dispatchInsets()

            assertThat(
                "the root does not consume the top inset, so the app bar draws behind the status bar",
                reviewer.rootLayout.paddingTop,
                equalTo(0),
            )
            assertThat(
                "app bar content is pushed clear of the status bar",
                reviewer.toolbarContainer.paddingTop,
                equalTo(24.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `app bar and counts bar are padded past a side navigation bar and cutout`() =
        withReviewer { reviewer ->
            // landscape with 3-button navigation: the navigation bar is a side inset and the
            // camera cutout is on the opposite side
            reviewer.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(
                "app bar content clears the cutout",
                reviewer.toolbarContainer.paddingLeft,
                equalTo(32.dp.toPx(targetContext)),
            )
            assertThat(
                "app bar content clears the side navigation bar",
                reviewer.toolbarContainer.paddingRight,
                equalTo(48.dp.toPx(targetContext)),
            )

            val sideMargin = targetContext.resources.getDimensionPixelSize(CommonR.dimen.side_margin)
            assertThat(
                "the counts bar keeps its side margin past the cutout",
                reviewer.countsBar.paddingLeft,
                equalTo(32.dp.toPx(targetContext) + sideMargin),
            )
            assertThat(
                "the counts bar keeps its side margin past the navigation bar",
                reviewer.countsBar.paddingRight,
                equalTo(48.dp.toPx(targetContext) + sideMargin),
            )
        }

    @Test
    fun `the card is padded past a side navigation bar and cutout`() =
        withReviewer { reviewer ->
            reviewer.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat("card clears the cutout", reviewer.cardContainer.paddingLeft, equalTo(32.dp.toPx(targetContext)))
            assertThat(
                "card clears the side navigation bar",
                reviewer.cardContainer.paddingRight,
                equalTo(48.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `answer buttons clear the navigation bar`() =
        withReviewer { reviewer ->
            reviewer.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                "the answer area rests a navigation bar's height above the screen's bottom",
                reviewer.answerArea.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
            assertThat(
                "the card sits above the answer area and needs no bottom inset",
                reviewer.cardContainer.paddingBottom,
                equalTo(0),
            )
        }

    @Test
    fun `answer buttons at the top - the card clears the navigation bar`() =
        withReviewer(answerButtonsPosition = "top") { reviewer ->
            reviewer.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                "the card is the bottom-most element and clears the navigation bar",
                reviewer.cardContainer.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
            assertThat(
                "the answer area is at the top and needs no bottom inset",
                reviewer.answerArea.paddingBottom,
                equalTo(0),
            )
        }

    @Test
    fun `no answer buttons - the type-answer area clears the navigation bar`() =
        withReviewer(answerButtonsPosition = "none") { reviewer ->
            reviewer.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                "the bottom area (holding the type-answer field) clears the navigation bar",
                reviewer.bottomArea.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `the answer area clears the keyboard`() =
        withReviewer { reviewer ->
            // edge-to-edge disables adjustResize: the answer buttons - and the type-answer
            // field above them - are kept above the keyboard via the ime inset
            reviewer.dispatchInsets(navBarBottom = 48.dp, imeBottom = 300.dp)

            assertThat(
                "the answer area rests above the keyboard",
                reviewer.answerArea.paddingBottom,
                equalTo(300.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `immersive review - the counts bar clears the camera cutout`() {
        FullScreenMode.setPreference(targetContext.sharedPrefs(), FullScreenMode.BUTTONS_ONLY)
        withReviewer { reviewer ->
            val baseTopPadding = reviewer.countsBar.paddingTop
            // bars hidden: only the camera cutout insets the content
            reviewer.dispatchInsets(cutoutTop = 32.dp, barsVisible = false)

            assertThat(
                "the counts bar sits at the top of the window and clears the cutout",
                reviewer.countsBar.paddingTop,
                equalTo(baseTopPadding + 32.dp.toPx(targetContext)),
            )
        }
    }

    @Test
    fun `immersive review - hidden bars - the counts bar keeps clear of the rounded corners`() {
        FullScreenMode.setPreference(targetContext.sharedPrefs(), FullScreenMode.BUTTONS_ONLY)
        withReviewer { reviewer ->
            val baseTopPadding = reviewer.countsBar.paddingTop
            // landscape: the camera cutout is a side inset, so no visible inset pushes the
            // counts below the display's rounded corners; the hidden status bar's region is
            // still reported by the stable insets
            reviewer.dispatchInsets(barsVisible = false)

            assertThat(
                "the counts rest a status bar's height below the top edge, as they did " +
                    "pre-edge-to-edge under SYSTEM_UI_FLAG_LAYOUT_STABLE, clear of the " +
                    "display's rounded corners",
                reviewer.countsBar.paddingTop,
                equalTo(baseTopPadding + 24.dp.toPx(targetContext)),
            )
        }
    }

    @Test
    fun `immersive review - the answer area's color extends under a revealed navigation bar`() {
        FullScreenMode.setPreference(targetContext.sharedPrefs(), FullScreenMode.BUTTONS_ONLY)
        withReviewer { reviewer ->
            reviewer.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                "the buttons rest above the navigation bar via painted padding: the " +
                    "showAnswerColor strip runs to the screen edge, matching normal review",
                reviewer.answerArea.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
            assertThat(
                "no unpainted margin below the answer area",
                reviewer.answerArea.marginBottom,
                equalTo(0),
            )
            assertThat(
                "no spurious top inset on the answer area (previously applied by fitsSystemWindows)",
                reviewer.answerArea.paddingTop,
                equalTo(0),
            )
            assertThat(
                "the overlaid toolbar is pushed clear of the revealed status bar",
                reviewer.toolbarContainer.paddingTop,
                equalTo(24.dp.toPx(targetContext)),
            )
        }
    }

    @Test
    fun `immersive review - no stripe with the answer buttons at the top - issue 14201`() {
        FullScreenMode.setPreference(targetContext.sharedPrefs(), FullScreenMode.BUTTONS_ONLY)
        withReviewer(answerButtonsPosition = "top") { reviewer ->
            reviewer.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                "the answer area is at the top: the navigation bar inset (the 'stripe') no longer pads it",
                reviewer.answerArea.paddingBottom,
                equalTo(0),
            )
            assertThat(
                "the card is the bottom-most element and clears the navigation bar",
                reviewer.cardContainer.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
        }
    }

    @Test
    fun `immersive review - hidden bars - the answer area keeps clear of the gesture area`() {
        FullScreenMode.setPreference(targetContext.sharedPrefs(), FullScreenMode.BUTTONS_ONLY)
        withReviewer { reviewer ->
            // gesture navigation: the bars are hidden, but their region (the gesture area,
            // by the display's rounded corners) is still reported by the stable insets
            reviewer.dispatchInsets(navBarStableBottom = 24.dp, barsVisible = false)

            assertThat(
                "the buttons rest on the gesture area, as they did pre-edge-to-edge " +
                    "under SYSTEM_UI_FLAG_LAYOUT_STABLE - via painted padding, so the " +
                    "answer area's color still reaches the screen edge",
                reviewer.answerArea.paddingBottom,
                equalTo(24.dp.toPx(targetContext)),
            )
            assertThat(
                "no unpainted margin below the answer area",
                reviewer.answerArea.marginBottom,
                equalTo(0),
            )
        }
    }

    @Test
    fun `immersive review - a hidden 3-button bar reserves only a gesture-sized strip`() {
        FullScreenMode.setPreference(targetContext.sharedPrefs(), FullScreenMode.BUTTONS_ONLY)
        withReviewer { reviewer ->
            // 3-button navigation: the hidden bar's full height stays in the stable insets,
            // but nothing is displayed under the buttons while it is hidden
            reviewer.dispatchInsets(navBarStableBottom = 48.dp, barsVisible = false)

            assertThat(
                "the buttons rest a gesture-bar strip above the screen edge - matching " +
                    "gesture navigation - rather than the hidden bar's full height",
                reviewer.answerArea.paddingBottom,
                equalTo(24.dp.toPx(targetContext)),
            )
        }
    }

    @Test
    fun `immersive review - hide everything - the answer area keeps the revealed bars' size`() {
        FullScreenMode.setPreference(targetContext.sharedPrefs(), FullScreenMode.FULLSCREEN_ALL_GONE)
        withReviewer { reviewer ->
            // the answer area is only shown alongside transiently revealed bars: while they
            // are hidden it stays sized for them, so the fade-out does not shift the
            // buttons - they disappear at the end of the fade, not the start
            reviewer.dispatchInsets(navBarStableBottom = 48.dp, barsVisible = false)

            assertThat(
                "the hidden answer area keeps the revealed navigation bar's inset",
                reviewer.answerArea.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
        }
    }

    @Test
    fun `immersive review - the buttons hold their place until a hiding bar has faded`() {
        FullScreenMode.setPreference(targetContext.sharedPrefs(), FullScreenMode.BUTTONS_ONLY)
        withReviewer { reviewer ->
            reviewer.dispatchInsets(navBarBottom = 48.dp)

            // the system animates the bars away: the final (hidden) insets arrive at the
            // start of the animation, while the bar fades for its duration
            val hide = WindowInsetsAnimation(WindowInsets.Type.systemBars(), null, 200)
            reviewer.window.decorView.dispatchWindowInsetsAnimationPrepare(hide)
            reviewer.dispatchInsets(navBarStableBottom = 48.dp, barsVisible = false)

            assertThat(
                "the buttons hold their place while the bar is still fading",
                reviewer.answerArea.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )

            reviewer.window.decorView.dispatchWindowInsetsAnimationEnd(hide)
            assertThat(
                "once the bar has faded, the buttons drop to their resting strip",
                reviewer.answerArea.paddingBottom,
                equalTo(24.dp.toPx(targetContext)),
            )
        }
    }

    @SuppressLint("NewApi") // as above: API 30+ dispatch, run on a current SDK
    @Test
    fun `immersive review - the buttons lift immediately as a bar is revealed`() {
        FullScreenMode.setPreference(targetContext.sharedPrefs(), FullScreenMode.BUTTONS_ONLY)
        withReviewer { reviewer ->
            reviewer.dispatchInsets(navBarStableBottom = 48.dp, barsVisible = false)

            // revealing also animates, but the buttons must not wait: they lift clear of
            // the incoming bar at the start of its animation
            val show = WindowInsetsAnimation(WindowInsets.Type.systemBars(), null, 200)
            reviewer.window.decorView.dispatchWindowInsetsAnimationPrepare(show)
            reviewer.dispatchInsets(navBarBottom = 48.dp)

            assertThat(
                "the buttons lift at the start of the reveal",
                reviewer.answerArea.paddingBottom,
                equalTo(48.dp.toPx(targetContext)),
            )
        }
    }

    @Test
    fun `immersive review - the answer area's background spans a side cutout like the counts bar`() {
        FullScreenMode.setPreference(targetContext.sharedPrefs(), FullScreenMode.BUTTONS_ONLY)
        withReviewer { reviewer ->
            // landscape with a camera notch: the cutout is a side inset; the bars are hidden
            reviewer.dispatchInsets(cutoutLeft = 32.dp, barsVisible = false)

            assertThat(
                "the answer area itself is padded past the cutout, so its background spans the full width",
                reviewer.answerArea.paddingLeft,
                equalTo(32.dp.toPx(targetContext)),
            )
            assertThat(
                "the bottom area no longer insets the answer area, whose background reaches the screen edge",
                reviewer.bottomArea.paddingLeft,
                equalTo(0),
            )
        }
    }

    @Test
    fun `the type-answer field is padded past a side navigation bar and cutout`() =
        withReviewer { reviewer ->
            val baseLeft = reviewer.typeAnswerField.paddingLeft
            val baseRight = reviewer.typeAnswerField.paddingRight
            reviewer.dispatchInsets(navBarRight = 48.dp, cutoutLeft = 32.dp)

            assertThat(
                "the field keeps its own padding in addition to the cutout inset",
                reviewer.typeAnswerField.paddingLeft,
                equalTo(baseLeft + 32.dp.toPx(targetContext)),
            )
            assertThat(
                "the field keeps its own padding in addition to the navigation bar inset",
                reviewer.typeAnswerField.paddingRight,
                equalTo(baseRight + 48.dp.toPx(targetContext)),
            )
        }

    @Test
    fun `immersive review - hide everything - the card clears the camera cutout`() {
        FullScreenMode.setPreference(targetContext.sharedPrefs(), FullScreenMode.FULLSCREEN_ALL_GONE)
        withReviewer { reviewer ->
            // FULLSCREEN_ALL_GONE lays the card out at the top of the window
            reviewer.dispatchInsets(cutoutTop = 32.dp, barsVisible = false)

            assertThat(
                "the card clears the cutout",
                reviewer.cardContainer.paddingTop,
                equalTo(32.dp.toPx(targetContext)),
            )
        }
    }

    @Test
    fun `immersive review - hide everything - the top inset is not applied twice`() {
        FullScreenMode.setPreference(targetContext.sharedPrefs(), FullScreenMode.FULLSCREEN_ALL_GONE)
        withReviewer { reviewer ->
            reviewer.dispatchInsets(cutoutTop = 32.dp, barsVisible = false)
            // relayout: the card is positioned below the mic toolbar layer
            advanceRobolectricLooper()

            assertThat(
                "the empty mic toolbar layer must not gain phantom height from the top inset: " +
                    "the card is laid out below it, so any height here doubles the gap",
                reviewer.micLayer.height,
                equalTo(0),
            )
            assertThat(
                "the card content starts exactly one cutout-height from the window top",
                reviewer.cardContainer.topInWindow + reviewer.cardContainer.paddingTop,
                equalTo(32.dp.toPx(targetContext)),
            )
        }
    }

    @Test
    fun `immersive review - hidden top bar - the card clears the camera cutout`() {
        FullScreenMode.setPreference(targetContext.sharedPrefs(), FullScreenMode.BUTTONS_ONLY)
        targetContext.sharedPrefs().edit { putBoolean("showTopbar", false) }
        withReviewer { reviewer ->
            // with 'Show top bar' disabled, the card is laid out at the top of the window
            reviewer.dispatchInsets(cutoutTop = 32.dp, barsVisible = false)

            assertThat(
                "the card clears the cutout",
                reviewer.cardContainer.paddingTop,
                equalTo(32.dp.toPx(targetContext)),
            )
        }
    }

    @Test
    fun `immersive review - controls fade out and back in with the bars`() {
        FullScreenMode.setPreference(targetContext.sharedPrefs(), FullScreenMode.FULLSCREEN_ALL_GONE)
        withReviewer { reviewer ->
            // the startup hideSystemBars() has already faded the controls out with the bars
            advanceRobolectricLooper()
            assertThat("the toolbar is hidden with the bars", reviewer.toolbarContainer.visibility, equalTo(View.GONE))
            assertThat("the answer area is hidden with the bars", reviewer.answerArea.visibility, equalTo(View.GONE))

            // the user swipes the bars back into view
            reviewer.dispatchInsets(navBarBottom = 48.dp, barsVisible = true)
            advanceRobolectricLooper()
            assertThat("the toolbar returns with the bars", reviewer.toolbarContainer.visibility, equalTo(View.VISIBLE))
            assertThat("the answer area returns with the bars", reviewer.answerArea.visibility, equalTo(View.VISIBLE))
            assertThat(
                "the overlaid app bar fades in fully opaque: a translucent overlay is tinted " +
                    "by the content behind it, splitting the status bar and app bar colors",
                reviewer.toolbarContainer.alpha,
                equalTo(1.0f),
            )

            reviewer.dispatchInsets(barsVisible = false)
            advanceRobolectricLooper()
            assertThat("the toolbar fades back out", reviewer.toolbarContainer.visibility, equalTo(View.GONE))
        }
    }

    @Test
    fun `taps on the answer area's inset band press the button above it`() =
        withReviewer { reviewer ->
            reviewer.dispatchInsets(navBarBottom = 48.dp)
            advanceRobolectricLooper()

            assertThat("the card starts on the question side", reviewer.isDisplayingAnswer, equalTo(false))
            // tap the showAnswerColor band painted below 'Show answer'
            reviewer.tapBottomBand(below = R.id.flashcard_layout_flip)
            advanceRobolectricLooper()
            assertThat(
                "a tap on the band below 'Show answer' flips the card",
                reviewer.isDisplayingAnswer,
                equalTo(true),
            )

            // the ease buttons replace the flip button; the band under 'Good' answers Good
            reviewer.tapBottomBand(below = R.id.flashcard_layout_ease3)
            advanceRobolectricLooper()
            assertThat(
                "a tap on the band below an ease button answers the card",
                reviewer.isDisplayingAnswer,
                equalTo(false),
            )
            assertThat("the card was answered 'Good'", lastAnsweredEase(), equalTo(REVLOG_EASE_GOOD))
        }

    @Test
    fun `taps on the answer area's side band press the outer button beside it`() =
        withReviewer { reviewer ->
            reviewer.dispatchInsets(navBarBottom = 48.dp, cutoutLeft = 24.dp)
            advanceRobolectricLooper()

            assertThat(
                "the answer area clears the cutout",
                reviewer.answerArea.paddingLeft,
                equalTo(24.dp.toPx(targetContext)),
            )
            // tap inside the cutout strip, level with 'Show answer'
            reviewer.answerArea.tap(x = 1f, y = 1f)
            advanceRobolectricLooper()
            assertThat(
                "a tap on the band beside 'Show answer' flips the card",
                reviewer.isDisplayingAnswer,
                equalTo(true),
            )
        }

    @Test
    fun `answer buttons at the top - taps on the side band press the outer button beside it`() =
        withReviewer(answerButtonsPosition = "top") { reviewer ->
            reviewer.dispatchInsets(cutoutLeft = 24.dp)
            advanceRobolectricLooper()

            assertThat(
                "the answer area clears the cutout",
                reviewer.answerArea.paddingLeft,
                equalTo(24.dp.toPx(targetContext)),
            )
            reviewer.answerArea.tap(x = 1f, y = 1f)
            advanceRobolectricLooper()
            assertThat(
                "a tap on the band beside 'Show answer' flips the card",
                reviewer.isDisplayingAnswer,
                equalTo(true),
            )
        }

    @Test
    fun `large answer buttons - the band below a bottom-row button presses only that button`() {
        // large answer buttons are laid out in two rows: Hard and Easy above Again and Good
        targetContext.sharedPrefs().edit { putBoolean("showLargeAnswerButtons", true) }
        withReviewer { reviewer ->
            reviewer.dispatchInsets(navBarBottom = 48.dp)
            advanceRobolectricLooper()
            reviewer.tapBottomBand(below = R.id.flashcard_layout_flip)
            advanceRobolectricLooper()
            assertThat("the ease buttons are shown", reviewer.isDisplayingAnswer, equalTo(true))

            val hardBounds = reviewer.boundsInAnswerArea(R.id.flashcard_layout_ease2)
            val againBounds = reviewer.boundsInAnswerArea(R.id.flashcard_layout_ease1)
            assertThat("'Hard' is laid out above 'Again'", hardBounds.bottom, equalTo(againBounds.top))

            // press, without releasing, the band below 'Again'
            reviewer.answerArea.touch(MotionEvent.ACTION_DOWN, x = againBounds.exactCenterX(), y = reviewer.answerArea.height - 1f)
            // inside a CoordinatorLayout, a press is only confirmed after the tap timeout
            advanceRobolectricLooper()
            assertThat(
                "the band presses 'Again'",
                reviewer.findViewById<View>(R.id.flashcard_layout_ease1).isPressed,
                equalTo(true),
            )
            assertThat(
                "the band leaves 'Hard', above 'Again', alone",
                reviewer.findViewById<View>(R.id.flashcard_layout_ease2).isPressed,
                equalTo(false),
            )
        }
    }

    /** The ease recorded for the most recent answer */
    private fun lastAnsweredEase(): Long = col.db.queryLongScalar("select ease from revlog order by id desc limit 1")

    /** The bounds of [button], in the answer area's coordinates */
    private fun Reviewer.boundsInAnswerArea(
        @IdRes button: Int,
    ): Rect {
        val view = findViewById<View>(button)
        return Rect().also {
            view.getDrawingRect(it)
            answerArea.offsetDescendantRectToMyCoords(view, it)
        }
    }

    /** Taps the showAnswerColor band painted [below] a button */
    private fun Reviewer.tapBottomBand(
        @IdRes below: Int,
    ) = answerArea.tap(x = boundsInAnswerArea(below).exactCenterX(), y = answerArea.height - 1f)

    private fun View.tap(
        x: Float,
        y: Float,
    ) {
        touch(MotionEvent.ACTION_DOWN, x, y)
        touch(MotionEvent.ACTION_UP, x, y)
    }

    private fun View.touch(
        action: Int,
        x: Float,
        y: Float,
    ) {
        val event = MotionEvent.obtain(0, 0, action, x, y, 0)
        dispatchTouchEvent(event)
        event.recycle()
    }

    private val Reviewer.rootLayout: View
        get() = findViewById(R.id.root_layout)

    private val Reviewer.toolbarContainer: View
        get() = findViewById(R.id.toolbar_container)

    private val Reviewer.countsBar: View
        get() = findViewById(R.id.top_bar)

    private val Reviewer.cardContainer: View
        get() = findViewById(R.id.flashcard)

    private val Reviewer.micLayer: View
        get() = findViewById(R.id.mic_tool_bar_layer)

    private val View.topInWindow: Int
        get() = IntArray(2).also { getLocationInWindow(it) }[1]

    private val Reviewer.bottomArea: View
        get() = findViewById(R.id.bottom_area_layout)

    private val Reviewer.typeAnswerField: View
        get() = findViewById(R.id.answer_field)

    private val Reviewer.answerArea: ViewGroup
        get() = findViewById(R.id.answer_options_layout)

    private fun withReviewer(
        answerButtonsPosition: String? = null,
        block: (Reviewer) -> Unit,
    ) {
        answerButtonsPosition?.let { position ->
            targetContext.sharedPrefs().edit {
                putString(targetContext.getString(R.string.answer_buttons_position_preference), position)
            }
        }
        addBasicNote("Hello", "World")
        block(ReviewerTest.startReviewer(this))
    }
}

/** The ease recorded in the revlog for a 'Good' answer */
private const val REVLOG_EASE_GOOD = 3L
