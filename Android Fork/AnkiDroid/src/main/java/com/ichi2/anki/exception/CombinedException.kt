// SPDX-License-Identifier: GPL-3.0-or-later

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
