// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.content.Context

/** The active [DatabaseErrorDialogs], set during app startup via [DatabaseErrorDialogs.register]. */
lateinit var databaseErrorDialogs: DatabaseErrorDialogs
    private set

/** Shows the app's database error dialogs, keeping callers decoupled from `AnkiActivity`. */
interface DatabaseErrorDialogs {
    fun showLoadFailed(
        context: Context,
        exception: Exception,
    )

    fun canConfirmDatabaseCheck(context: Context): Boolean

    fun confirmDatabaseCheck(context: Context)

    companion object {
        /** Use during app startup to set the global [DatabaseErrorDialogs] instance. */
        fun register(dialogs: DatabaseErrorDialogs) {
            databaseErrorDialogs = dialogs
        }
    }
}
