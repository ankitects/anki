// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.search.SearchNode
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.launchFragmentInContainer
import com.ichi2.testutils.withCollectionAccessOn
import com.ichi2.testutils.withFragment
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PageFragmentHiddenPageRegressionTest : RobolectricTest() {
    @Test
    fun `hidden page request fails if the fragment is removed before being shown`() =
        runTest {
            launchFragmentInContainer<Statistics>().use { scenario ->
                val (page, activity) = scenario.withFragment { this to requireActivity() }
                activity.supportFragmentManager.commitNow { hide(page) }
                val request =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        runCatching {
                            page.handlePostRequest(
                                PostRequestUri("/_anki/searchInBrowser"),
                                SearchNode.getDefaultInstance().toByteArray(),
                            )
                        }
                    }
                advanceUntilIdle()

                activity.supportFragmentManager.commitNow { remove(page) }
                advanceUntilIdle()

                assertTrue(request.isCompleted, "Removing a hidden page must terminate its queued request")
                assertTrue(request.await().isFailure, "A removed page must fail its queued UI request")
                assertNull(shadowOf(activity).nextStartedActivity)
            }
        }

    @Test
    fun `browser request waits for a hidden statistics page to be shown again`() =
        runTest {
            launchFragmentInContainer<Statistics>().use { scenario ->
                val (page, activity) = scenario.withFragment { this to requireActivity() }
                advanceUntilIdle()
                val shadow = shadowOf(activity)
                withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                    val request =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            page.handlePostRequest(
                                PostRequestUri("/_anki/searchInBrowser"),
                                SearchNode.getDefaultInstance().toByteArray(),
                            )
                        }

                    // Bottom navigation hides Statistics without lowering its lifecycle.
                    activity.supportFragmentManager.commitNow { hide(page) }
                    assertTrue(page.isHidden)
                    assertEquals(Lifecycle.State.RESUMED, page.lifecycle.currentState)
                    advanceUntilIdle()
                    val hiddenLaunch = shadow.nextStartedActivity

                    activity.supportFragmentManager.commitNow { show(page) }
                    request.await()

                    assertNull(hiddenLaunch, "A hidden statistics page must not open the browser over another tab")
                    assertEquals(CardBrowser::class.java.name, assertNotNull(shadow.nextStartedActivity).component?.className)
                }
            }
        }
}
