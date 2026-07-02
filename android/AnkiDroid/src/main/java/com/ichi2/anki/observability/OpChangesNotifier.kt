/*
 *  Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>
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

package com.ichi2.anki.observability

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wraps a routine that returns OpChanges* or similar undo info,
 * notifying [ChangeManager] subscribers of the changes.
 *
 * **[block] must return an OpChanges subtype as its last expression.**
 * [T] is inferred from [block]'s return type. If the last expression returns `Unit`
 * (e.g. a `Timber` call), [ChangeManager.notifySubscribers] throws
 * "unhandled change type of class 'class kotlin.Unit'" at runtime.
 */
suspend fun <T : Any> undoableOp(
    handler: Any? = null,
    block: Collection.() -> T,
): T =
    withCol {
        block()
    }.also {
        withContext(Dispatchers.Main) {
            ChangeManager.notifySubscribers(it, handler)
        }
    }
