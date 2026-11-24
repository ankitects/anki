// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.waitUntil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class CsvImporterTest : InstrumentedTest() {
    @Test
    fun dismissingAMissingFileErrorClosesTheImporter() {
        ActivityScenario.launch<SingleFragmentActivity>(CsvImporter.getIntent(testContext, "/missing-21926.csv")).use { scenario ->
            lateinit var page: CsvImporter
            scenario.onActivity { page = it.fragment as CsvImporter }
            waitUntil(timeout = 30.seconds, message = { "CSV error page was not displayed" }) {
                page.evaluateJavascript("document.querySelector('.error-box button') !== null") == "true"
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                page.webViewLayout.evaluateJavascript("document.querySelector('.error-box button').click()")
            }
            waitUntil(timeout = 10.seconds, message = { "CSV error acknowledgement did not close the importer" }) {
                scenario.state == Lifecycle.State.DESTROYED
            }
        }
    }

    /** Evaluates [script] in the WebView, returning the JSON-encoded result. */
    private fun CsvImporter.evaluateJavascript(script: String): String {
        val result = CompletableDeferred<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webViewLayout.evaluateJavascript(script) { result.complete(it) }
        }
        return runBlocking { withTimeout(10.seconds) { result.await() } }
    }
}
