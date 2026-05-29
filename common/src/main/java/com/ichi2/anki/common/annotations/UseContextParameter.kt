// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.annotations

/**
 * Use when code should be converted to use context parameters (Kotlin 2.2.0)
 *
 * Context parameters are not yet supported by AnkiDroid
 * https://github.com/JLLeitschuh/ktlint-gradle/issues/912
 *
 * @param toExtend the name of the class to extend
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
annotation class UseContextParameter(
    val toExtend: String,
)
