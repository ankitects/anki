// SPDX-License-Identifier: GPL-3.0-or-later

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
