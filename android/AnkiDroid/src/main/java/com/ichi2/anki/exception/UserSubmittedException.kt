//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki.exception

import java.lang.RuntimeException

/** An exception sent by user for sending report manually  */
class UserSubmittedException(
    message: String?,
) : RuntimeException(message)
