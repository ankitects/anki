// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki.utils

/**
 * Annotates `libAnki` functionality which is not in the upstream
 *  [pylib](https://github.com/ankitects/anki/tree/main/pylib)
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.EXPRESSION,
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.TYPEALIAS,
)
@Retention(AnnotationRetention.SOURCE)
internal annotation class NotInPyLib
