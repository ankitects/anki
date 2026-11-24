// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import net.ankiweb.rsdroid.Backend

/**
 * Global variables for LibAnki, supporting AnkiDroid tests which use both `CollectionManager` and
 * `TestCollectionManager`
 *
 * These variables should not be used in production AnkiDroid code outside `CollectionManager`
 */
object LibAnki {
    /**
     * ⚠️ Use CollectionManager to access this
     *
     * The currently active backend
     *
     * The backend is long-lived, and will generally only be closed when switching interface
     * languages or changing schema versions. A closed backend cannot be reused, and a new one
     * must be created.
     */
    @Deprecated("Only use this inside CollectionManagers", level = DeprecationLevel.WARNING)
    var backend: Backend? = null

    /**
     * The current collection.
     */
    @Deprecated("Only use this inside CollectionManagers", level = DeprecationLevel.WARNING)
    var collection: Collection? = null
}
