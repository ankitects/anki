// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.DatabaseErrorDialog.UninstallListItem.Companion.createNoStorageList
import com.ichi2.utils.cancelable
import com.ichi2.utils.listItemsAndMessage
import com.ichi2.utils.show
import timber.log.Timber

/**
 * Inform the user that Android 13+ has permanently revoked access to `WRITE_EXTERNAL_STORAGE`.
 * Typically due to app permissions being revoked from unused apps
 *
 * Their collection is safe, but inaccessible
 *
 * Provide recovery options
 *
 * Issue 14423
 *
 * @see DatabaseErrorDialogType.DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL
 */
object AndroidPermanentlyRevokedPermissionsDialog {
    @SuppressLint("CheckResult")
    fun show(context: AnkiActivity) {
        val listItemData = createNoStorageList()

        val message =
            context.getString(
                CommonString.directory_revoked_after_inactivity,
                "WRITE_EXTERNAL_STORAGE",
                getCurrentAnkiDroidDirectoryPath(context),
            )
        AlertDialog.Builder(context).show {
            listItemsAndMessage(
                message = message,
                listItemData.map { context.getString(it.stringRes) },
            ) { dialog: DialogInterface, index: Int ->
                val listItem = listItemData[index]
                listItem.onClick(context)
                if (listItem.dismissesDialog) {
                    dialog.dismiss()
                }
            }
            cancelable(false)
        }
    }

    private fun getCurrentAnkiDroidDirectoryPath(context: Context): String =
        try {
            CollectionHelper.getCurrentAnkiDroidDirectory(context).absolutePath
        } catch (e: Exception) {
            Timber.w(e)
            context.getString(CommonString.card_browser_unknown_deck_name)
        }
}
