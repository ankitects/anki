// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.webkit.WebView
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.preferences.reviewer.ViewerAction
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.clickShowAnswer
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.goodButton
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.workarounds.SafeWebViewLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** Tests scroll behaviour of [ReviewerFragment]. */
@RunWith(AndroidJUnit4::class)
class ReviewerFragmentScrollTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var testDeckId: DeckId = 0
    private val newReviewerOptionsKey get() = testContext.getString(R.string.new_reviewer_options_key)
    private var originalNewReviewerOptions: Boolean? = null

    @Before
    fun setUp() {
        val preferences = testContext.sharedPrefs()
        originalNewReviewerOptions = preferences.all[newReviewerOptionsKey] as Boolean?
        preferences.edit { putBoolean(newReviewerOptionsKey, true) }
        testDeckId = col.decks.addNormalDeckWithName("ReviewerFragmentScrollTest-${UUID.randomUUID()}").id
        col.decks.select(testDeckId)
    }

    @After
    fun tearDown() {
        testContext.sharedPrefs().edit {
            val original = originalNewReviewerOptions
            if (original == null) remove(newReviewerOptionsKey) else putBoolean(newReviewerOptionsKey, original)
        }
        col.decks.remove(listOf(testDeckId))
    }

    /** Issue 5182: revealing a cloze without an answer anchor must preserve the viewport. */
    @Test
    fun showingAnswerPreservesScrollPosition() {
        addTallClozeCard("answer")

        withReviewer {
            waitForCloze("[...]")
            scrollDown()
            clickShowAnswer()
            waitForCloze("answer")
            awaitAnimationFrames()

            assertEquals(SCROLL_POSITION.toDouble(), pageScrollY, "scroll position after revealing the cloze")
        }
    }

    @Test
    fun showingAnswerResetsZoomAndPreservesScrollPosition() {
        addTallClozeCard("answer")

        withReviewer {
            waitForCloze("[...]")
            val originalScale = pageScale
            zoomIn()
            scrollDown()
            clickShowAnswer()
            waitForCloze("answer")
            waitForScale(originalScale)
            awaitAnimationFrames()

            assertEquals(SCROLL_POSITION.toDouble(), pageScrollY, "scroll position after resetting zoom on card flip")
        }
    }

    @Test
    fun answerAnchorTakesPrecedenceAfterResettingZoom() {
        addNoteUsingBasicNoteType("${SPACER}question", "answer$SPACER").firstCard(col).update { did = testDeckId }

        withReviewer {
            waitUntil(timeout = 30.seconds, message = { "Question did not load" }) {
                evaluateScript("document.body.textContent.includes('question')") == "true"
            }
            val originalScale = pageScale
            zoomIn()
            scrollDown()
            clickShowAnswer()
            waitForScale(originalScale)
            awaitAnimationFrames()

            waitUntil(message = { "Answer anchor was not scrolled into view after resetting zoom" }) {
                evaluateScript("Math.abs(document.getElementById('answer')?.getBoundingClientRect().top) <= 1") == "true"
            }
        }
    }

    /** Force answer rendering to race ahead of the native zoom reset. */
    @Test
    fun answerRenderingWaitsForZoomReset() {
        addTallClozeCard("answer")

        withReviewer {
            waitForCloze("[...]")
            val originalScale = pageScale
            zoomIn()
            scrollDown()

            evaluateScript(
                """
                window.zoomResetQueued = false;
                _queueAction(() => { window.zoomResetQueued = true; });
                ankidroid.waitForZoomReset($originalScale);
                _showAnswer("$SPACER<hr id='answer'>answer$SPACER", 'card');
                """.trimIndent(),
            )
            waitUntil(message = { "Previous card updates did not finish" }) {
                evaluateScript("window.zoomResetQueued") == "true"
            }
            // The answer must still be pending after the queued JavaScript microtasks:
            // we deliberately have not requested the native zoom reset yet.
            assertEquals(
                "false",
                evaluateScript("document.getElementById('answer') !== null"),
                "answer rendered before the zoom reset",
            )

            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                zoomBy(0.5f)
            }
            waitForScale(originalScale)
            waitUntil(message = { "Answer anchor was not scrolled into view after the delayed zoom reset" }) {
                evaluateScript("Math.abs(document.getElementById('answer')?.getBoundingClientRect().top) <= 1") == "true"
            }
        }
    }

    @Test
    fun answerAnchorTakesPrecedenceOverScrollPosition() {
        addNoteUsingBasicNoteType("${SPACER}question", "answer$SPACER").firstCard(col).update { did = testDeckId }

        withReviewer {
            waitUntil(timeout = 30.seconds, message = { "Question did not load" }) {
                evaluateScript("document.body.textContent.includes('question')") == "true"
            }
            scrollDown()
            clickShowAnswer()

            waitUntil(message = { "Answer anchor was not scrolled into view" }) {
                evaluateScript("Math.abs(document.getElementById('answer')?.getBoundingClientRect().top) <= 1") == "true"
            }
        }
    }

    @Test
    fun nextQuestionStartsAtTop() = checkNextQuestionStartsAtTop(zoom = false)

    @Test
    fun nextQuestionResetsZoomAndStartsAtTop() = checkNextQuestionStartsAtTop(zoom = true)

    private fun checkNextQuestionStartsAtTop(zoom: Boolean) {
        addTallClozeCard("first")
        addTallClozeCard("second")

        withReviewer {
            waitForCloze("[...]")
            clickShowAnswer()
            waitUntil(message = { "Answer did not load" }) {
                evaluateScript("['first', 'second'].includes(document.querySelector('.cloze')?.textContent)") == "true"
            }
            val originalScale = pageScale
            if (zoom) zoomIn()
            scrollDown()
            composeRule.goodButton().performClick()
            waitForCloze("[...]")
            waitForScale(originalScale)
            awaitAnimationFrames()

            assertEquals(0.0, pageScrollY, "scroll position on the next question")
        }
    }

    /** Moving to the next card resets the viewport even when the answer was never shown. */
    @Test
    fun buryingQuestionStartsNextQuestionAtTop() = checkBuryingQuestionStartsNextQuestionAtTop(zoom = false)

    @Test
    fun buryingQuestionResetsZoomAndStartsNextQuestionAtTop() = checkBuryingQuestionStartsNextQuestionAtTop(zoom = true)

    private fun checkBuryingQuestionStartsNextQuestionAtTop(zoom: Boolean) {
        addLabelledClozeCard("first")
        addLabelledClozeCard("second")

        withReviewer { fragment ->
            val buriedLabel = waitForAnyLabel()
            val originalScale = pageScale
            if (zoom) zoomIn()
            scrollDown()
            fragment.executeAction(ViewerAction.BURY_CARD)
            waitUntil(message = { "Next question did not load" }) {
                currentLabel.let { it.isNotEmpty() && it != buriedLabel }
            }
            waitForScale(originalScale)
            awaitAnimationFrames()

            assertEquals(0.0, pageScrollY, "scroll position after burying a card")
        }
    }

    private fun addTallClozeCard(answer: String) {
        addClozeNote("$SPACER{{c1::$answer}}$SPACER").firstCard(col).update { did = testDeckId }
    }

    /** Adds a tall cloze card whose question side is identifiable by [label]. */
    private fun addLabelledClozeCard(label: String) {
        addClozeNote("<div id='label'>$label</div>$SPACER{{c1::answer}}$SPACER")
            .firstCard(col)
            .update { did = testDeckId }
    }

    private fun withReviewer(block: SafeWebViewLayout.(ReviewerFragment) -> Unit) {
        ActivityScenario.launch<CardViewerActivity>(ReviewerFragment.getIntent(testContext)).use { scenario ->
            lateinit var fragment: ReviewerFragment
            scenario.onActivity { activity ->
                fragment = activity.fragment as ReviewerFragment
            }
            fragment.binding.webViewLayout.block(fragment)
        }
    }

    private fun ReviewerFragment.executeAction(action: ViewerAction) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel.executeAction(action)
        }
    }

    /** The label of the currently displayed card, or `""` if no card is displayed. */
    private val SafeWebViewLayout.currentLabel: String
        get() = evaluateScript("document.getElementById('label')?.textContent ?? ''").removeSurrounding("\"")

    /** Waits for a labelled card to display, returning its label. */
    private fun SafeWebViewLayout.waitForAnyLabel(): String {
        waitUntil(timeout = 30.seconds, message = { "Question did not load" }) { currentLabel.isNotEmpty() }
        return currentLabel
    }

    private fun clickShowAnswer() = composeRule.clickShowAnswer()

    private fun SafeWebViewLayout.waitForCloze(text: String) {
        waitUntil(timeout = 30.seconds, message = { "Cloze did not display '$text'" }) {
            evaluateScript("document.querySelector('.cloze')?.textContent") == "\"$text\""
        }
    }

    private fun SafeWebViewLayout.scrollDown() {
        evaluateScript("window.scrollTo(0, $SCROLL_POSITION)")
        waitUntil(message = { "Card did not scroll to $SCROLL_POSITION" }) {
            pageScrollY == SCROLL_POSITION.toDouble()
        }
    }

    private val SafeWebViewLayout.pageScrollY: Double
        get() = evaluateScript("window.scrollY").toDouble()

    private val SafeWebViewLayout.pageScale: Double
        get() = evaluateScript("window.visualViewport.scale").toDouble()

    @Suppress("DEPRECATION") // Native zoom limits indicate whether WebView has applied the viewport.
    private val SafeWebViewLayout.canZoomIn: Boolean
        get() {
            var canZoom = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                canZoom = (getChildAt(0) as WebView).canZoomIn()
            }
            return canZoom
        }

    private fun SafeWebViewLayout.zoomIn() {
        waitUntil(message = { "WebView did not become ready to zoom" }) { canZoomIn }
        val originalScale = pageScale
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            zoomBy(2f)
        }
        waitForScale(originalScale * 2)
    }

    private fun SafeWebViewLayout.waitForScale(expectedScale: Double) {
        waitUntil(message = { "Expected page scale $expectedScale, got $pageScale" }) {
            abs(pageScale - expectedScale) < 0.01
        }
    }

    /** Allow queued layout and scroll changes to finish before asserting the viewport position. */
    private fun SafeWebViewLayout.awaitAnimationFrames() {
        evaluateScript(
            """
            window.scrollTestReady = false;
            requestAnimationFrame(() => requestAnimationFrame(() => { window.scrollTestReady = true; }));
            """.trimIndent(),
        )
        waitUntil(message = { "WebView did not render" }) {
            evaluateScript("window.scrollTestReady") == "true"
        }
    }

    /** Evaluates [script] on the UI thread and returns its JSON-encoded result. */
    private fun SafeWebViewLayout.evaluateScript(script: String): String {
        val result = CompletableDeferred<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            evaluateJavascript(script) { result.complete(it) }
        }
        return runBlocking { withTimeout(10.seconds) { result.await() } }
    }

    companion object {
        private const val SCROLL_POSITION = 800

        /** Filler which makes a card taller than the viewport, so that it can be scrolled. */
        private const val SPACER = "<div style='height: 1500px'>Tall card content</div>"

        @JvmStatic
        @BeforeClass
        fun checkWebView() = ensureWebViewIsSupported()
    }
}
