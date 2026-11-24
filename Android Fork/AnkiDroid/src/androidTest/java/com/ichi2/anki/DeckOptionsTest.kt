// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.pages.DeckOptions
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.workarounds.SafeWebViewLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.json.JSONTokener
import org.junit.Rule
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Tests for [DeckOptions] */
class DeckOptionsTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(GrantStoragePermission.storagePermission)

    /**
     * Regression test for [#20929](https://github.com/ankidroid/Anki-Android/issues/20929)
     */
    @Test
    fun easyDaysSliderHasWrapperInstalled() =
        // Brittle:
        // * does not reproduce the original bug
        // * does not react to an upstream fix
        withDeckOptions { deckOptions ->
            val pointerEvents =
                deckOptions.pollJavascript<String>(
                    """
                    (function() {
                        const el = document.querySelector('.easy-days-settings input[type="range"]');
                        return el ? getComputedStyle(el).pointerEvents : null;
                    })()
                    """.trimIndent(),
                )
            assertThat("Easy Days slider should have pointer-events disabled", pointerEvents, equalTo("none"))
        }

    private fun withDeckOptions(block: suspend (DeckOptions) -> Unit) =
        runBlocking {
            val intent = DeckOptions.getIntent(testContext, Consts.DEFAULT_DECK_ID)
            ActivityScenario.launch<SingleFragmentActivity>(intent).use { scenario ->
                val fragment =
                    suspendCancellableCoroutine { cont ->
                        scenario.onActivity { activity ->
                            cont.resume(activity.fragment as DeckOptions)
                        }
                    }
                block(fragment)
            }
        }
}

/**
 * Returns `null` for a `null`/`undefined` result, [T] otherwise
 */
private suspend fun <T : Any> DeckOptions.pollJavascript(
    js: String,
    timeout: Duration = 10.seconds,
    interval: Duration = 100.milliseconds,
): T = pollUntilNotNull(timeout, interval) { webViewLayout.awaitJavascript(js) }

/**
 * Returns `null` for a `null`/`undefined` result, [T] otherwise
 */
@Suppress("UNCHECKED_CAST")
private suspend fun <T : Any> SafeWebViewLayout.awaitJavascript(js: String): T? {
    val result =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                evaluateJavascript(js) { value -> cont.resume(value) }
            }
        }
    if (result == "null") return null
    return JSONTokener(result).nextValue() as? T
}

/**
 * Calls [block] every [interval] until a non-null value is returned
 *
 * @throws kotlinx.coroutines.TimeoutCancellationException if [timeout] elapses
 */
private suspend fun <T : Any> pollUntilNotNull(
    timeout: Duration,
    interval: Duration,
    block: suspend () -> T?,
): T =
    withTimeout(timeout) {
        while (true) {
            val result = block()
            if (result != null) return@withTimeout result
            delay(interval)
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }
