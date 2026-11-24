// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import timber.log.Timber
import java.lang.reflect.Field

/**
 * @param fieldName name of the field
 * @return a [Field] with `isAccessible` set to true, or null if the field could not be found
 */
inline fun <reified T : Any> getAccessibleJavaField(fieldName: String): Field? {
    val field =
        T::class.java.declaredFields.firstOrNull { it.name == fieldName }?.apply {
            isAccessible = true
        }
    if (field == null) {
        Timber.w("Could not find field $fieldName in class ${T::class.java}")
    }
    return field
}
