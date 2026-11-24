// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.TestInputMethodRule
import com.ichi2.anki.testutil.awaitJavascript
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.evaluate
import com.ichi2.anki.testutil.focusInput
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import org.junit.Rule
import org.junit.Test

/** Exercises Chromium, the bridge, and the input connection Android delivers to a real IME. */
class TypeAnswerWebViewTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @get:Rule
    val keyboard = TestInputMethodRule()

    @Test
    fun suggestionsAndSelectionFollowTheFocusedField() =
        withAnswerPage {
            focusInput("ordinary")
            val baseline = keyboard.awaitEditor().inputType
            keyboard.perform("commit", "ordinary text")
            awaitJavascript("ordinary.value === 'ordinary text'")
            evaluate("document.getElementById('ordinary').setSelectionRange(2, 5)")

            focusInput("answer")
            keyboard.awaitEditor(InputType.TYPE_NULL)
            keyboard.perform("commit", "été")
            awaitJavascript("answer.value === 'été'")
            evaluate("document.getElementById('answer').setSelectionRange(1, 2)")

            focusInput("ordinary")
            keyboard.awaitEditor(baseline)
            awaitJavascript("ordinary.value === 'ordinary text' && ordinary.selectionStart === 2 && ordinary.selectionEnd === 5")

            focusInput("answer")
            keyboard.awaitEditor(InputType.TYPE_NULL)
            awaitJavascript("answer.value === 'été' && answer.selectionStart === 1 && answer.selectionEnd === 2")
        }

    @Test
    fun markedFieldsKeepNoSuggestWhenCardScriptsStopFocusPropagation() =
        withAnswerPage {
            focusInput("answer")
            keyboard.awaitEditor(InputType.TYPE_NULL)
            evaluate(
                """
                window.keyboardStates = [];
                const nativeKeyboard = AnkiDroidKeyboard;
                AnkiDroidKeyboard = { setNoSuggest(enabled) {
                    keyboardStates.push(enabled);
                    nativeKeyboard.setNoSuggest(enabled);
                }};
                """.trimIndent(),
            )
            focusInput("otherAnswer")
            awaitJavascript("keyboardStates.length > 0 && keyboardStates.every(enabled => enabled === true)")
            keyboard.awaitEditor(InputType.TYPE_NULL)
            keyboard.perform("commit", "second")
            awaitJavascript("otherAnswer.value === 'second'")
            focusInput("ordinary")
            keyboard.awaitEditor()
        }

    @Test
    fun noSuggestAcceptsCommittedUnicodeAndCompositionFromIme() =
        withAnswerPage {
            focusInput("answer")
            keyboard.awaitEditor(InputType.TYPE_NULL)
            keyboard.perform("commit", "été ")
            keyboard.perform("compose", "に")
            keyboard.perform("compose", "日本")
            keyboard.perform("finish")
            awaitJavascript("answer.value === 'été 日本'")
        }

    @Test
    fun noSuggestAcceptsCharactersSentByIme() =
        withAnswerPage {
            focusInput("answer")
            keyboard.awaitEditor(InputType.TYPE_NULL)
            keyboard.perform("chars", "été")
            awaitJavascript("answer.value === 'été'")
        }

    @Test
    fun noSuggestRequestsDoneAndDeliversEnter() =
        withAnswerPage {
            focusInput("answer")
            keyboard.awaitEditor(InputType.TYPE_NULL, imeAction = EditorInfo.IME_ACTION_DONE)
            keyboard.perform("done")
            awaitJavascript("document.body.dataset.key === 'Enter'")
        }

    @Test
    fun restoresSuggestionsWhenFocusingAReplacementInput() =
        withAnswerPage {
            focusInput("ordinary")
            val baseline = keyboard.awaitEditor().inputType
            focusInput("answer")
            keyboard.awaitEditor(InputType.TYPE_NULL)
            // Removing the focused node does not necessarily fire blur.
            evaluate("document.body.innerHTML = '<input id=next>'")
            focusInput("next")
            keyboard.awaitEditor(baseline)
        }

    private fun withAnswerPage(block: TypeAnswerWebView.() -> Unit) {
        ensureWebViewIsSupported()
        val intent = SingleFragmentActivity.getIntent(testContext, Fragment::class)
        ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
            lateinit var webView: TypeAnswerWebView
            scenario.onActivity { webView = TypeAnswerWebView(it) }
            try {
                scenario.onActivity { activity ->
                    activity.setContentView(webView)
                    webView.settings.javaScriptEnabled = true
                    webView.settings.allowFileAccess = true
                    webView.loadDataWithBaseURL(
                        "file:///android_asset/",
                        """
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <div onfocusin="event.stopPropagation()" onfocusout="event.stopPropagation()">
                            <input id="answer" data-ankidroid-nosuggest="true" enterkeyhint="done"
                                onkeydown="document.body.dataset.key = event.key">
                            <input id="otherAnswer" data-ankidroid-nosuggest="true" enterkeyhint="done">
                            <input id="ordinary">
                        </div>
                        <script src="scripts/type-answer.js"></script>
                        """.trimIndent(),
                        "text/html",
                        null,
                        null,
                    )
                }
                webView.awaitJavascript("document.readyState === 'complete' && document.getElementById('answer') !== null")
                webView.block()
            } finally {
                scenario.onActivity {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.destroy()
                }
            }
        }
    }
}
