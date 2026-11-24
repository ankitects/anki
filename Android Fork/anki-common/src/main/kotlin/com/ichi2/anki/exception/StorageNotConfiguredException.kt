// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.exception

import com.ichi2.anki.common.storage.StorageDecision

/**
 * Thrown when the collection is accessed before the user has chosen where it should be stored
 * (the [storage decision][StorageDecision] is `Undecided`).
 *
 * Use `StorageAccessException` if a known path is unusable.
 */
class StorageNotConfiguredException(
    msg: String? = null,
    e: Throwable? = null,
) : Exception(msg, e)
