/*
 *  Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.anki.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures AnkiDroid cold-startup time in two configurations so the
 * before/after impact of the baseline profile is visible in a single run:
 *
 * - [startupCompilationNone] — [CompilationMode.None]: no AOT compilation,
 *   all code is JIT'd on the fly. This is the "before" baseline.
 * - [startupCompilationBaselineProfile] — [CompilationMode.Partial] with
 *   [BaselineProfileMode.Require]: AOT-compiles exactly the methods listed
 *   in `baseline-prof.txt`. `Require` fails the test if the profile isn't
 *   installed, so any improvement is guaranteed to come from the profile.
 *
 * Both methods run [StartupMode.COLD] with 10 iterations. Compare the
 * median of `timeToInitialDisplayMs` across the two methods.
 *
 * **This benchmark is for local, manual use only.** It requires a connected
 * physical device, has no pass/fail assertions, and should not be included
 * in CI test suites. Run with:
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkBenchmarkAndroidTest
 * ```
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupCompilationNone() = startup(CompilationMode.None())

    @Test
    fun startupCompilationBaselineProfile() = startup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun startup(mode: CompilationMode) {
        val targetPackage =
            InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: throw IllegalStateException("targetAppId not passed as instrumentation runner arg")

        rule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(StartupTimingMetric()),
            iterations = 10,
            startupMode = StartupMode.COLD,
            compilationMode = mode,
        ) {
            pressHome()
            startActivityAndWait()
        }
    }
}
