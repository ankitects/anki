// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.annotations

/**
 * Use when code needs unit tests
 *
 * This annotation is intended to:
 * 1. Be used with all new code contributions if a test is not provided to:
 *    * Show new contributors that we care about testing without delaying their first commits
 *    * Ensure the spec is written with the code fresh in mind
 *    * Ensure the requirement doesn't go stale in a GitHub issue
 *
 * For the future:
 * 2. Let maintainers prioritize tests in terms of difficulty and priority
 * 3. List 'good first tests' for new contributors (Google Summer of Code, etc...)
 * 4. List 'small chunks' of work for shorter periods of maintainer attention
 *
 * @param value the explanation for why the test is required.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.EXPRESSION,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
)
@Repeatable
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class NeedsTest(
    val value: String,
)
