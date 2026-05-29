// SPDX-License-Identifier: GPL-3.0-or-later

@file:KotlinCleanup(
    "Remove all TextUtils references then see if we can remove " +
        "@RunWith(AndroidJUnit4::class) to speed up tests (if no other Android references)",
)

package com.ichi2.anki.common.utils.annotation

/** Use when code can be changed after further conversion to Kotlin */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.EXPRESSION,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FILE,
)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
@MustBeDocumented
annotation class KotlinCleanup(
    val value: String,
)
