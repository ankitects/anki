/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.logging

import android.util.Log
import timber.log.Timber

/** Enable verbose error logging and do method tracing to put the Class name as log tag */
class RobolectricDebugTree : Timber.DebugTree() {
    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        // This is noisy in test environments
        when (tag) {
            "Backend\$checkMainThreadOp" -> return
            "Media" -> if (priority == Log.VERBOSE && message.startsWith("dir")) return
            "CollectionManager" -> if (message.startsWith("blocked main thread")) return
        }
        super.log(priority, tag, message, t)
    }
}
