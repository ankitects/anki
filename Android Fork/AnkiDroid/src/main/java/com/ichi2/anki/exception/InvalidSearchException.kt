// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.exception

/** Thrown when a search string is incorrect/malformed */
class InvalidSearchException(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
