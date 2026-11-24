// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.app.Activity
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.ichi2.anki.R
import com.ichi2.anki.Reviewer
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.checkWithTimeout
import com.ichi2.anki.testutil.AvoidDayRolloverRule
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.TestInputMethodRule
import com.ichi2.anki.testutil.awaitDisplayed
import com.ichi2.anki.testutil.awaitJavascript
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.focusInput
import com.ichi2.anki.testutil.goodButton
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.UUID
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Checks the real reviewers' HTML, script loading, and WebView keyboard support together. */
@RunWith(Parameterized::class)
class HtmlTypeAnswerReviewerTest : InstrumentedTest() {
    @JvmField // required for Parameter
    @Parameterized.Parameter
    var newReviewer = false

    @JvmField // required for Parameter
    @Parameterized.Parameter(1)
    var autoFocus = false

    @get:Rule
    val keyboard = TestInputMethodRule()

    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @get:Rule
    val avoidDayRollover = AvoidDayRolloverRule()

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private var originalPreferences = emptyMap<String, Any?>()
    private var originalDeckId: DeckId = 0
    private var originalNoteTypeId: NoteTypeId = 0
    private var testDeckId: DeckId = 0
    private val testNoteTypeIds = mutableListOf<NoteTypeId>()

    @Before
    fun setUp() {
        ensureWebViewIsSupported()
        configurePreferences()
        setUpCollection()
    }

    private fun configurePreferences() {
        val preferences = testContext.sharedPrefs()
        val newReviewerKey = testContext.getString(R.string.new_reviewer_options_key)
        val doubleTapKey = testContext.getString(R.string.double_tap_timeout_pref_key)
        originalPreferences =
            listOf(newReviewerKey, "useInputTag", "autoFocusTypeInAnswer", doubleTapKey).associateWith { preferences.all[it] }
        preferences.edit {
            putBoolean(newReviewerKey, newReviewer)
            putBoolean("useInputTag", true)
            putBoolean("autoFocusTypeInAnswer", autoFocus)
            // These tests intentionally grade immediately after Done.
            putInt(doubleTapKey, 0)
        }
    }

    private fun setUpCollection() {
        originalDeckId = col.decks.selected()
        originalNoteTypeId = col.notetypes.current(forDeck = false).id
        testDeckId = col.decks.addNormalDeckWithName("HtmlTypeAnswerReviewerTest-${UUID.randomUUID()}").id
        col.decks.select(testDeckId)
        addAnswerCard("First", noSuggest = true)
    }

    private fun addAnswerCard(
        front: String,
        noSuggest: Boolean,
    ) {
        val noteType = col.notetypes.copy(assertNotNull(col.notetypes.byName("Basic (type in the answer)")))
        testNoteTypeIds.add(noteType.id)
        val filter = if (noSuggest) "nosuggest:type" else "type"
        noteType.templates[0].qfmt = "<span id='question'>{{Front}}</span>{{$filter:Back}}<input id='ordinary'>"
        col.notetypes.save(noteType)
        val note =
            col.newNote(noteType).apply {
                setField(0, front)
                setField(1, "été")
            }
        col.addNote(note, testDeckId)
    }

    @After
    fun tearDown() {
        restorePreferences()
        restoreCollection()
    }

    private fun restorePreferences() {
        testContext.sharedPrefs().edit {
            for ((key, original) in originalPreferences) {
                when (original) {
                    null -> remove(key)
                    is Boolean -> putBoolean(key, original)
                    is Int -> putInt(key, original)
                    else -> error("Unexpected preference value: $key=$original")
                }
            }
        }
    }

    private fun restoreCollection() {
        if (testDeckId != 0L) col.decks.remove(listOf(testDeckId))
        for (id in testNoteTypeIds) col.notetypes.remove(id)
        if (originalDeckId != 0L) col.decks.select(originalDeckId)
        if (originalNoteTypeId != 0L) col.notetypes.setCurrent(assertNotNull(col.notetypes.get(originalNoteTypeId)))
    }

    @Test
    fun onlyTheMarkedAnswerDisablesSuggestions() =
        withReviewer { webView ->
            // Let reviewer autofocus finish before moving to another field.
            focusAnswer(webView, noSuggest = true)
            webView.focusInput("ordinary")
            val baseline = keyboard.awaitEditor().inputType
            webView.focusInput("typeans")
            keyboard.awaitEditor(InputType.TYPE_NULL, imeAction = EditorInfo.IME_ACTION_DONE)
            webView.focusInput("ordinary")
            keyboard.awaitEditor(baseline)
        }

    @Test
    fun doneShowsTheAnswerWithTheTextSentByTheIme() =
        withReviewer { webView ->
            focusAnswer(webView, noSuggest = true)
            keyboard.perform("chars", "été")
            webView.awaitJavascript("document.getElementById('typeans').value === 'été'")
            keyboard.perform("done")
            webView.awaitJavascript("document.querySelector('#typeans .typeGood')?.textContent === 'été'")
        }

    @Test
    fun suggestionsFollowActualCardTransitions() {
        addAnswerCard("Second", noSuggest = false)
        addAnswerCard("Third", noSuggest = true)
        withReviewer { webView ->
            for ((question, noSuggest) in listOf("First" to true, "Second" to false, "Third" to true)) {
                webView.awaitJavascript("document.getElementById('question')?.textContent === '$question'")
                focusAnswer(webView, noSuggest)
                keyboard.perform("commit", "été")
                webView.awaitJavascript("document.getElementById('typeans').value === 'été'")
                keyboard.perform("done")
                webView.awaitJavascript("document.querySelector('#typeans .typeGood')?.textContent === 'été'")
                if (question != "Third") {
                    if (newReviewer) {
                        composeRule.awaitDisplayed { goodButton() }.performClick()
                    } else {
                        onView(withId(R.id.flashcard_layout_ease3))
                            .also { it.checkWithTimeout(matches(isDisplayed())) }
                            .perform(click())
                    }
                }
            }
        }
    }

    private fun focusAnswer(
        webView: WebView,
        noSuggest: Boolean,
    ) {
        if (autoFocus) {
            webView.awaitJavascript("document.activeElement?.id === 'typeans'")
        } else {
            webView.focusInput("typeans")
        }
        if (noSuggest) {
            keyboard.awaitEditor(InputType.TYPE_NULL, imeAction = EditorInfo.IME_ACTION_DONE)
        } else {
            keyboard.awaitEditor()
        }
    }

    private fun withReviewer(block: (WebView) -> Unit) {
        ActivityScenario.launch<Activity>(Reviewer.getIntent(testContext)).use { scenario ->
            lateinit var webView: WebView
            scenario.onActivity { activity ->
                webView =
                    if (newReviewer) {
                        val fragment = assertIs<ReviewerFragment>(assertIs<CardViewerActivity>(activity).fragment)
                        assertIs<TypeAnswerWebView>(fragment.binding.webViewLayout.getChildAt(0))
                    } else {
                        assertNotNull(assertIs<Reviewer>(activity).webView)
                    }
            }
            webView.awaitJavascript("document.getElementById('typeans') !== null")
            block(webView)
        }
    }

    companion object {
        @JvmStatic // required for Parameters
        @Parameterized.Parameters(name = "newReviewer={0}, autoFocus={1}")
        fun reviewers() = listOf(arrayOf(false, false), arrayOf(false, true), arrayOf(true, false), arrayOf(true, true))
    }
}
