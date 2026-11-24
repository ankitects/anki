// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.core.app.PendingIntentCompat
import androidx.core.content.IntentCompat
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.dialogs.BackupPromptDialog
import timber.log.Timber
import java.io.File

class ExportSharedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val target = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java)
        Timber.i("Export shared with %s", target?.packageName)
        if (target == null || target.packageName == context.packageName) return
        val path = intent.getStringExtra(EXTRA_EXPORT_PATH) ?: return
        BackupPromptDialog.onFileExported(AnkiDroidApp.wrapForActiveProfile(context), File(path))
    }

    companion object {
        private const val EXTRA_EXPORT_PATH = "exportPath"

        fun getIntentSender(
            context: Context,
            file: File,
        ): IntentSender? =
            PendingIntentCompat
                .getBroadcast(
                    context,
                    0,
                    Intent(context, ExportSharedReceiver::class.java).putExtra(EXTRA_EXPORT_PATH, file.path),
                    PendingIntent.FLAG_UPDATE_CURRENT,
                    true,
                )?.intentSender
    }
}
