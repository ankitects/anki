/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
