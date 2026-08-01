/*
 * Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.utils.android.HandlerUtils
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class HandlerUtilsTest {
    @Test
    fun checkHandlerFunctionExecution() {
        var value = false
        HandlerUtils.executeFunctionUsingHandler { value = true }
        runUiThreadTasksIncludingDelayedTasks()
        assertThat("Function was executed", value, equalTo(true))
    }

    @Test
    fun checkHandlerFunctionExecutionWithDelay() {
        var value = false
        val initialTime = SystemClock.uptimeMillis()

        HandlerUtils.executeFunctionWithDelay(1000) { value = true }

        runUiThreadTasksIncludingDelayedTasks()
        assertThat("Function was executed", value, equalTo(true))

        val duration = SystemClock.uptimeMillis() - initialTime

        // Assert true if difference between current time and initial time is around 1000 milliseconds
        assertThat("Delay is around 1 second", duration.toDouble(), closeTo(1000.0, 10.0))
    }
}
