// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleDestroyedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.search.searchNode
import com.ichi2.anki.browser.CardBrowserViewModel.Companion.EXTRA_ALL_DECKS
import com.ichi2.anki.browser.CardBrowserViewModel.Companion.EXTRA_SEARCH_QUERY
import com.ichi2.testutils.withCollectionAccessOn
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SearchInBrowserTest : RobolectricTest() {
    private lateinit var controller: ActivityController<FragmentActivity>
    private val searchInput = searchNode { deck = "Default" }.toByteArray()

    @Before
    fun setUpActivity() {
        controller = Robolectric.buildActivity(FragmentActivity::class.java).setup().also(::saveControllerForCleanup)
    }

    @Test
    fun `search waits if the activity stops during collection access`() =
        runTest {
            val shadow = shadowOf(controller.get())
            val request = queueSearch()

            controller.pause().stop()
            runCurrent()
            assertNull(shadow.nextStartedActivity)
            assertFalse(request.isCompleted)

            controller.restart().start().resume()
            assertContentEquals(searchInput, request.await())
            val intent = assertNotNull(shadow.nextStartedActivity)
            assertEquals(CardBrowser::class.java.name, intent.component?.className)
            assertEquals("deck:Default", intent.getStringExtra(EXTRA_SEARCH_QUERY))
            assertTrue(intent.getBooleanExtra(EXTRA_ALL_DECKS, false))

            controller.pause().stop()
            controller.restart().start().resume()
            runCurrent()
            assertNull(shadow.nextStartedActivity)
        }

    @Test
    fun `destroying the activity cancels a waiting search`() =
        runTest {
            val request = queueSearch()

            controller.pause().stop()
            runCurrent()
            assertFalse(request.isCompleted)

            controller.destroy()
            assertFailsWith<LifecycleDestroyedException> { request.await() }
            assertNull(shadowOf(controller.get()).nextStartedActivity)
        }

    /** Start a search, leaving its collection access queued until [runCurrent]. */
    private suspend fun TestScope.queueSearch() =
        withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
            async(start = CoroutineStart.UNDISPATCHED) { controller.get().searchInBrowser(searchInput, awaitPageShown = {}) }
        }
}
