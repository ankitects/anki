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

package com.ichi2.testutils

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.libanki.testutils.TestCollectionManager
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Adapts [CollectionManager] to [TestCollectionManager]
 */
object ProductionCollectionManager : TestCollectionManager {
    override fun getColUnsafe() = CollectionManager.getColUnsafe()

    override suspend fun discardBackend() {
        CollectionManager.discardBackend()
    }

    /** @see CollectionManager.setTestDispatcher */
    fun setTestDispatcher(dispatcher: CoroutineDispatcher) {
        CollectionManager.setTestDispatcher(dispatcher)
    }
}
