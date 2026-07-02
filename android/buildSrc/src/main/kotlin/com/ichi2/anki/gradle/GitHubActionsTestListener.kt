// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import kotlin.time.Duration.Companion.milliseconds

/**
 * Extracts the values [TestSummaryService] needs from each root test-suite
 * completion and forwards them. Holding only a [Provider] reference keeps
 * the listener configuration-cache safe.
 */
class GitHubActionsTestListener(
    private val service: Provider<TestSummaryService>,
) : TestListener {
    override fun beforeSuite(suite: TestDescriptor) {}

    override fun afterTest(
        testDescriptor: TestDescriptor,
        result: TestResult,
    ) {}

    override fun beforeTest(testDescriptor: TestDescriptor) {}

    override fun afterSuite(
        suite: TestDescriptor,
        result: TestResult,
    ) {
        if (suite.parent != null) return // only log for the root suite
        service.get().append(
            TestSummaryService.Row(
                suite = suite.displayName,
                result = result.resultType.name,
                duration = (result.endTime - result.startTime).milliseconds,
                testCount = result.testCount,
                passed = result.successfulTestCount,
                failed = result.failedTestCount,
                skipped = result.skippedTestCount,
            ),
        )
    }
}
