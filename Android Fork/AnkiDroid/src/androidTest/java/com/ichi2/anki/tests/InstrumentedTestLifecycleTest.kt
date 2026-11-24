// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tests

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import net.ankiweb.rsdroid.Backend
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstrumentedTestLifecycleTest : InstrumentedTest() {
    private lateinit var backend: Backend
    private lateinit var translation: String

    @get:Rule
    val backendUser =
        object : ExternalResource() {
            override fun after() {
                // Activity rules may still run UI work that needs translations during teardown.
                assertThat(backend.tr.browsingToggleShowingCardsNotes(), equalTo(translation))
            }
        }

    @Test
    fun backendRemainsOpenUntilRulesFinish() {
        // Keep the original instance so teardown cannot silently open a replacement backend.
        backend = CollectionManager.getBackend()
        translation = backend.tr.browsingToggleShowingCardsNotes()
    }
}
