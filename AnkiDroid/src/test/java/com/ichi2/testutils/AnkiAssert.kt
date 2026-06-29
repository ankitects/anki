// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import org.junit.AssumptionViolatedException
import kotlin.test.junit5.JUnit5Asserter

/** Asserts that the expression is `false` with an optional [message]. */
fun assertFalse(
    message: String? = null,
    actual: Boolean,
) {
    // This exists in JUnit, but we want to avoid JUnit as their `assertNotNull` does not use contracts
    // So, we want a method in a different namespace for `assertFalse`
    // JUnitAsserter doesn't contain it, so we add it in
    JUnit5Asserter.assertTrue(message, !actual)
}

/** Unconditionally skips the current test with [reason] reported as the skip message. */
fun skipTest(reason: String): Nothing = throw AssumptionViolatedException(reason)
