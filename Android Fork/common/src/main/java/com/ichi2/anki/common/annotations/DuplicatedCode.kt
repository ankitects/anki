// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023

package com.ichi2.anki.common.annotations

/**
 * Use when code is duplicated and will be refactored at a later date
 *
 * @param value the explanation for why the code is duplicated.
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
annotation class DuplicatedCode(
    val value: String,
)
