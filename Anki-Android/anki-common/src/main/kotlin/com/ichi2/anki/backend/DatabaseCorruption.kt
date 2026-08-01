// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.backend

/**
 * Global state which distinguishes between an inaccessible and a corrupt collection database.
 *
 * @see isDetected
 */
object DatabaseCorruption {
    /** `true` if backend database corruption is encountered. */
    var isDetected = false
}
