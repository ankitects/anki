// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.app.Activity
import android.content.Context
import com.ichi2.anki.common.utils.ext.findActivity
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DatabaseErrorDialogs
import com.ichi2.anki.dialogs.showDatabaseErrorDialogInDeckPicker

/** Shows [DatabaseErrorDialogs] through the [AnkiActivity] of the [Context]. */
internal object AnkiDroidDatabaseErrorDialogs : DatabaseErrorDialogs {
    override fun showLoadFailed(
        context: Context,
        exception: Exception,
    ) {
        val exceptionData = DatabaseErrorDialog.CustomExceptionData.fromException(exception)
        // TODO: move this decision into showDatabaseErrorDialogInDeckPicker without breaking #22931
        when (context) {
            is AnkiActivity -> context.showDatabaseErrorDialog(DatabaseErrorDialogType.DIALOG_LOAD_FAILED, exceptionData)
            is Activity -> context.showDatabaseErrorDialogInDeckPicker(DatabaseErrorDialogType.DIALOG_LOAD_FAILED, exceptionData)
        }
    }

    override fun canConfirmDatabaseCheck(context: Context): Boolean = context.findActivity<Activity>() != null

    override fun confirmDatabaseCheck(context: Context) {
        context.findActivity<Activity>()!!.showDatabaseErrorDialogInDeckPicker(
            errorDialogType = DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK,
        )
    }
}

/** Wires up [AnkiDroidDatabaseErrorDialogs] as the global [DatabaseErrorDialogs]. */
fun initializeDatabaseErrorDialogs() {
    DatabaseErrorDialogs.register(AnkiDroidDatabaseErrorDialogs)
}

/** The dialog's [Context] is wrapped (e.g. ContextThemeWrapper); walk the chain to find the activity. */
internal fun Context.findAnkiActivity(): AnkiActivity? = findActivity<AnkiActivity>()
