// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.common.annotations

/**
 * Indicates that a section of code is part of the legacy notifications system in place before
 * August 2025. Code flagged with this annotation is slated to be eventually deleted once the
 * review reminders system becomes stable.
 *
 * Also see all conditional points gated by Prefs.newReviewRemindersEnabled.
 *
 * Once all occurrences of both this annotation and Prefs.newReviewRemindersEnabled are no longer
 * present in the code base, the migration from the legacy notifications system to the new review
 * reminders system will be complete.
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
annotation class LegacyNotifications(
    val optionalReason: String = "",
)
