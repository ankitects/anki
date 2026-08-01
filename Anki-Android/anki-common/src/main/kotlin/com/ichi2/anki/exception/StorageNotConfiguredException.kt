// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.exception

/**
 * Thrown when the collection is accessed before the user has chosen where it should be stored
 * (the [storage decision][com.ichi2.anki.storage.StorageDecision] is `Undecided`).
 *
 * Use `StorageAccessException` if a known path is unusable.
 */
class StorageNotConfiguredException(
    msg: String? = null,
    e: Throwable? = null,
) : Exception(msg, e)
