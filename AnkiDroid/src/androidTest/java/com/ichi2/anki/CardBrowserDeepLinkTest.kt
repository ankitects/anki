// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Sending the `anki://x-callback-url/browser?search=` deep link opens a search in [CardBrowser].
 */
@RunWith(AndroidJUnit4::class)
class CardBrowserDeepLinkTest : InstrumentedTest() {
    @get:Rule
    val storagePermission = GrantStoragePermission.instance

    @Test
    fun deepLinkOpensCardBrowserWithSearch() {
        addNoteUsingBasicNoteType("dog", "barks")
        addNoteUsingBasicNoteType("cat", "meows")

        // implicit VIEW intent, constrained to AnkiDroid so the OS resolves it without a chooser
        val deepLink =
            Intent(Intent.ACTION_VIEW, "anki://x-callback-url/browser?search=dog".toUri()).apply {
                setPackage(testContext.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        testContext.startActivity(deepLink)

        waitForActivity<CardBrowser> {
            assertThat(
                "the deep link opens the browser on its search",
                viewModel.searchTerms,
                equalTo("dog"),
            )
        }
    }

    /**
     * Waits for an activity of type [T] to resume (the end of the deep-link chain), runs [block]
     * against it on the main thread, then finishes it. Fails if [T] does not resume in time.
     */
    private inline fun <reified T : Activity> waitForActivity(
        timeout: Duration = 10.seconds,
        crossinline block: T.() -> Unit,
    ) {
        val deadline = SystemClock.uptimeMillis() + timeout.inWholeMilliseconds
        var activity = TestUtils.activityInstance as? T
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        while (activity == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            SystemClock.sleep(50)
            activity = TestUtils.activityInstance as? T
        }
        val resolved =
            activity ?: fail("Timed out waiting for ${T::class.java.simpleName}; resumed = ${TestUtils.activityInstance}")
        try {
            instrumentation.runOnMainSync { resolved.block() }
        } finally {
            instrumentation.runOnMainSync { resolved.finish() }
        }
    }
}
