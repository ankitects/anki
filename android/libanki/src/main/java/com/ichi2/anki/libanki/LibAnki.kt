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
