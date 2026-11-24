// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>

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
