/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.destinations.DeferredNavigation
import com.ichi2.anki.common.preferences.sharedPrefs

/**
 * Marker interface for classes annotated with `@RunWith(AndroidJUnit4.class)` which do
 * not need access to the collection
 *
 * Classes should also be marked with `@Config(application = EmptyApplication::class)` for
 * performance improvements
 *
 * Use [JvmTest] if a reference to Android is not necessary but the collection is required
 *
 * Use [RobolectricTest] if access the collection is necessary
 */
interface AndroidTest : DeferredNavigation

val AndroidTest.targetContext: Context
    get() {
        return try {
            ApplicationProvider.getApplicationContext()
        } catch (e: IllegalStateException) {
            if (e.message != null && e.message!!.startsWith("No instrumentation registered!")) {
                // Explicitly ignore the inner exception - generates line noise
                throw IllegalStateException("Annotate class: '${javaClass.simpleName}' with '@RunWith(AndroidJUnit4::class)'")
            }
            throw e
        }
    }

/**
 * Returns an instance of [SharedPreferences] using the test context
 * @see [editPreferences] for editing
 */
fun AndroidTest.getSharedPrefs(): SharedPreferences = targetContext.sharedPrefs()

fun AndroidTest.getString(res: Int): String = targetContext.getString(res)

@Suppress("unused")
fun AndroidTest.getQuantityString(
    res: Int,
    quantity: Int,
    vararg formatArgs: Any,
): String = targetContext.resources.getQuantityString(res, quantity, *formatArgs)

/**
 * Allows editing of preferences, followed by a call to [apply][SharedPreferences.Editor.apply]:
 *
 * ```
 * editPreferences { putString("key", value) }
 * ```
 */
fun AndroidTest.editPreferences(action: SharedPreferences.Editor.() -> Unit) = getSharedPrefs().edit(action = action)
