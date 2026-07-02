/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.exception

private typealias ExceptionFormatter = (index: Int, ex: Throwable) -> String

/**
 * Combines multiple [exceptions] into a single aggregate exception with options to customize the
 * message.
 * @param messageOverride if not null it will be used as the [message] of this exception
 * @param messageFormatter if not null it will be used to format the message of each child
 * exception. Not used if messageOverride is set.
 */
class CombinedException(
    vararg val exceptions: Throwable,
    messageFormatter: ExceptionFormatter? = null,
    messageOverride: String? = null,
) : Exception() {
    override val message: String =
        when {
            messageOverride != null -> messageOverride
            messageFormatter != null ->
                exceptions
                    .mapIndexed { idx, ex -> messageFormatter(idx, ex) }
                    .joinToString("\n")

            else -> exceptions.joinToString("\n") { it.message ?: it.javaClass.simpleName }
        }

    companion object {
        fun from(values: List<Pair<String, Throwable>>): CombinedException? {
            if (values.isEmpty()) return null
            return CombinedException(
                exceptions = values.map { it.second }.toTypedArray(),
                messageOverride = values.joinToString("\n") { it.first },
            )
        }
    }
}
