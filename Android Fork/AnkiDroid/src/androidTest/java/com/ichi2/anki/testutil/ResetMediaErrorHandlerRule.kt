// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.AbstractFlashcardViewer
import com.ichi2.anki.cardviewer.MediaErrorHandler
import org.junit.rules.ExternalResource

/**
 * Resets the state of the [MediaErrorHandler] singleton.
 *
 * ```kotlin
 * // Close the activity before restoring the handler.
 * @get:Rule(order = 0)
 * val resetMediaErrorHandler = ResetMediaErrorHandlerRule()
 *
 * @get:Rule(order = 1)
 * val activityScenarioRule = ActivityScenarioRule(Reviewer::class.java)
 * ```
 *
 * Manually launched activities must be closed within the test.
 *
 * @see AbstractFlashcardViewer.mediaErrorHandler
 */
class ResetMediaErrorHandlerRule : ExternalResource() {
    private lateinit var originalHandler: MediaErrorHandler

    override fun before() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            originalHandler = AbstractFlashcardViewer.mediaErrorHandler
            AbstractFlashcardViewer.mediaErrorHandler = MediaErrorHandler()
        }
    }

    override fun after() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AbstractFlashcardViewer.mediaErrorHandler = originalHandler
        }
    }
}
