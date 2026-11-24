// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import androidx.fragment.app.commitNow
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.search.SearchNode
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PageFragmentRemovedDuringCollectionTest : RobolectricTest() {
    @Test
    fun `removing hidden page before collection reply fails its queued browser request`() = assertRemovedPageFails(hidden = true)

    @Test
    fun `removing shown page before collection reply fails its queued browser request`() = assertRemovedPageFails(hidden = false)

    private fun assertRemovedPageFails(hidden: Boolean) =
        runTest {
            launchFragmentInContainer<Statistics>().use { scenario ->
                val (page, activity) = scenario.withFragment { this to requireActivity() }
                advanceUntilIdle()
                if (hidden) activity.supportFragmentManager.commitNow { hide(page) }

                withCollectionAccessOn(StandardTestDispatcher(testScheduler)) {
                    val request =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            runCatching {
                                page.handlePostRequest(
                                    PostRequestUri("/_anki/searchInBrowser"),
                                    SearchNode.getDefaultInstance().toByteArray(),
                                )
                            }
                        }

                    // The page closes while the real search-string calculation is still queued.
                    activity.supportFragmentManager.commitNow { remove(page) }
                    advanceUntilIdle()

                    assertNull(shadowOf(activity).nextStartedActivity, "A removed page must not open the browser")
                    assertTrue(request.isCompleted, "Removing the page before collection access must terminate the request")
                    assertTrue(request.await().isFailure, "A request from a removed page must fail")
                }
            }
        }
}
