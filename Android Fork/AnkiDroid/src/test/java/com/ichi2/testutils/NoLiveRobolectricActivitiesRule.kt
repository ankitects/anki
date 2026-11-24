// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement

/**
 * After each test (including all `@After` methods), fails if Robolectric still has live activities.
 *
 * Runs as a [TestRule] so subclass `@After` cleanup that closes [androidx.test.core.app.ActivityScenario]
 * after `super.tearDown()` still runs before the check.
 */
class NoLiveRobolectricActivitiesRule : TestRule {
    private val cleanupFailures = mutableListOf<Throwable>()

    /** Report an owned controller's failure after subclass and rule cleanup have finished. */
    fun recordCleanupFailure(failure: Throwable) {
        cleanupFailures.add(failure)
    }

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val failures = mutableListOf<Throwable>()
                try {
                    base.evaluate()
                } catch (failure: Throwable) {
                    failures.add(failure)
                }
                failures.addAll(cleanupFailures)
                cleanupFailures.clear()
                try {
                    Robolectric.assertNoLiveActivities(
                        "test \"${description.methodName}\"",
                    )
                } catch (failure: Throwable) {
                    failures.add(failure)
                }
                MultipleFailureException.assertEmpty(failures)
            }
        }
}
