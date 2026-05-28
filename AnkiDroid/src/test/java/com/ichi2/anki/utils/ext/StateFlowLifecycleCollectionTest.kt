/*
 *  Copyright (c) 2026 HS0204(Hanseul Lee) <lhanseul0204@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.utils.ext

import android.content.Intent
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.RobolectricTest
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

// test for the deduplication logic in StateFlow.launchCollectionInLifecycleScope
@RunWith(RobolectricTestRunner::class)
class StateFlowLifecycleCollectionTest : RobolectricTest() {
    // scenario: background > value changes > resume triggers race
    // to check StateFlow logic ensure that all updates are delivered and none are potentially skipped
    @Test
    fun `concurrent value change does not skip UI update`() {
        val controller = Robolectric.buildActivity(AnkiActivity::class.java, Intent())
        saveControllerForCleanup(controller)
        val flow = MutableStateFlow(0)
        val concurrentFlow =
            ConcurrentStateFlow(flow) { emitted ->
                if (emitted == 1) flow.value = 2
            }
        val updates = mutableListOf<Int>()

        controller.create().start().resume()
        with(controller.get()) {
            concurrentFlow.launchCollectionInLifecycleScope { updates.add(it) }
        }
        advanceRobolectricLooper()

        assertEquals(listOf(0), updates)

        controller.pause().stop()
        advanceRobolectricLooper()
        flow.value = 1
        controller.start().resume()
        advanceRobolectricLooper()

        assertEquals(listOf(0, 1, 2), updates)
    }

    // simulates a concurrent value change between emission and the dedup check
    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    private class ConcurrentStateFlow(
        private val delegate: MutableStateFlow<Int>,
        private val onEmit: (Int) -> Unit,
    ) : StateFlow<Int> by delegate {
        override suspend fun collect(collector: FlowCollector<Int>): Nothing {
            delegate.collect { emitted ->
                onEmit(emitted)
                collector.emit(emitted)
            }
        }
    }
}
