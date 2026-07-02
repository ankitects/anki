/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.utils

import android.content.Context
import com.ichi2.anki.R

fun interface TranslatableException {
    fun getTranslatedMessage(context: Context): String
}

/**
 * Get an user-friendly error message out of an exception.
 * If the exception is a [TranslatableException], a localized error message is returned.
 *
 * TODO Special-case some of the most common exceptions thrown by the system or the library.
 */
fun Context.getUserFriendlyErrorText(e: Exception): String =
    if (e is TranslatableException) {
        e.getTranslatedMessage(this)
    } else {
        e.localizedMessage?.ifBlank { null }
            ?: e.message?.ifBlank { null }
            ?: e::class.simpleName?.ifBlank { null }
            ?: getString(R.string.error__etc__unknown_error)
    }

/**
 * Runs [action] and guards against [OutOfMemoryError] using a try-catch block.
 * @param action the code to run
 * @param onError optional listener to be notified when a [OutOfMemoryError] occurred
 * @return the result of successfully executing [action] or null if an [OutOfMemoryError] occurred
 */
fun <T> runWithOOMCheck(
    action: () -> T,
    onError: ((OutOfMemoryError) -> Unit)? = null,
) = try {
    action()
} catch (e: OutOfMemoryError) {
    onError?.invoke(e)
    null
}
