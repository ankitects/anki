/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.testutils

import com.ichi2.anki.utils.getAccessibleJavaField
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KCallable
import kotlin.reflect.full.createType

/** For use when checking to see if a KType is equal to another type */
inline fun <reified T> KCallable<*>.isType() = returnType == T::class.createType()

inline fun <reified T : Any> requireAccessibleJavaField(fieldName: String): Field =
    getAccessibleJavaField<T>(fieldName) ?: throw IllegalStateException(
        "Could not find `$fieldName` in class ${T::class.java}.",
    )

/**
 * Reverts a `lateinit` field to uninitialized
 */
inline fun <reified T : Any> uninitializeField(fieldName: String) = requireAccessibleJavaField<T>(fieldName).set(null, null)

/**
 * @param clazz Java class to get the field
 * @param methodName name of the method
 * @return a [Field] object with `isAccessible` set to true
 */
fun getJavaMethodAsAccessible(
    clazz: Class<*>,
    methodName: String,
    vararg parameterTypes: Class<*>,
): Method =
    clazz.getDeclaredMethod(methodName, *parameterTypes).apply {
        isAccessible = true
    }

inline fun <reified T> getInstanceFromClassName(javaClassName: String): T =
    Class.forName(javaClassName).getDeclaredConstructor().newInstance() as T
