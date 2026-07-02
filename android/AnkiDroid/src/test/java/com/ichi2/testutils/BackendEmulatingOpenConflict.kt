/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.testutils

import anki.backend.BackendError
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException
import net.ankiweb.rsdroid.BackendFactory
import org.mockito.Mockito

/** Test helper:
 * causes getCol to emulate an exception caused by having another AnkiDroid instance open on the same collection
 */
class BackendEmulatingOpenConflict : Backend() {
    override fun openCollection(
        collectionPath: String,
        mediaFolderPath: String,
        mediaDbPath: String,
    ) {
        val error = Mockito.mock(BackendError::class.java)
        throw BackendDbLockedException(error)
    }

    companion object {
        fun enable() {
            BackendFactory.setOverride { BackendEmulatingOpenConflict() }
        }

        fun disable() {
            BackendFactory.setOverride(null)
        }
    }
}
