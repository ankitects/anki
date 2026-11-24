// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.browser.IdsFile
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.previewer.PreviewerFragment
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission.storagePermission
import com.ichi2.anki.testutil.ensureWebViewIsSupported
import com.ichi2.anki.testutil.grantPermissions
import com.ichi2.anki.testutil.notificationPermission
import com.ichi2.anki.testutil.waitUntil
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/** Covers the MathJax loading path shared by the previewer and new study screen. */
class CardViewerMathJaxTest : InstrumentedTest() {
    @get:Rule
    val runtimePermissionRule = grantPermissions(storagePermission, notificationPermission)

    @Test
    fun mathJaxRendersInThePreviewer() {
        withCardViewer("""\(x^2\)""") { scenario ->
            val rendered = AtomicBoolean(false)
            waitUntil(timeout = 30.seconds, message = { "MathJax equation did not render" }) {
                scenario.onActivity { activity ->
                    val previewer = activity.fragment as PreviewerFragment
                    previewer.binding.webViewLayout.evaluateJavascript(
                        """
                        document.querySelector('#qa mjx-container mjx-math')?.getBoundingClientRect().height > 0
                            && getComputedStyle(document.getElementById('qa')).opacity === '1'
                        """.trimIndent(),
                    ) { rendered.set(it == "true") }
                }
                rendered.get()
            }
        }
    }

    private fun withCardViewer(
        front: String,
        block: (ActivityScenario<CardViewerActivity>) -> Unit,
    ) {
        ensureWebViewIsSupported()
        val note = addNoteUsingBasicNoteType(front)
        val idsFile = IdsFile(testContext.cacheDir, listOf(note.firstCard(col).id), IdsFile.Purpose.PREVIEW)
        val intent = PreviewerFragment.getIntent(testContext, idsFile, currentIndex = 0)
        try {
            ActivityScenario.launch<CardViewerActivity>(intent).use(block)
        } finally {
            idsFile.delete()
            col.backend.removeNotes(noteIds = listOf(note.id), cardIds = emptyList())
        }
    }
}
