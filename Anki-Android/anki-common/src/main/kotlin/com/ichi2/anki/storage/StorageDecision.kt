// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.storage

/**
 * Whether the user has chosen where AnkiDroid stores its collection.
 *
 * On a fresh install the location must be chosen explicitly (see [com.ichi2.anki.AnkiDroidFolder]); there is no
 * silent default, and the collection must not be opened until a choice has been made.
 */
sealed interface StorageDecision {
    /** A storage location has been chosen; the collection may be opened. */
    data object Decided : StorageDecision

    /** No storage location has been chosen yet; the collection must not be opened. */
    data object Undecided : StorageDecision
}
