// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 LUwUcifer <luwucifwer@proton.me>
package com.ichi2.anki.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.notifications.NotificationId
import com.ichi2.utils.TruncatedString
import com.ichi2.utils.copyToClipboard
import timber.log.Timber

/**
 * Copies [EXTRA_SYNC_ERROR_LOG] to the clipboard and dismisses the media sync notification.
 *
 * @see com.ichi2.anki.worker.SyncMediaWorker
 */
class CopyToClipboardReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val text =
            intent.getStringExtra(EXTRA_SYNC_ERROR_LOG) ?: run {
                Timber.w("CopyToClipboardReceiver: no error log found")
                showThemedToast(context, CommonString.something_wrong, shortLength = true)
                return
            }
        // only dismiss the notification once the text is safely on the clipboard
        if (context.copyToClipboard(TruncatedString.from(text))) {
            NotificationManagerCompat.from(context).cancel(NotificationId.SYNC_MEDIA)
        }
    }

    companion object {
        private const val EXTRA_SYNC_ERROR_LOG = "syncErrorLog"

        /**
         * Method for getting an intent for this receiver.
         * @return An intent which copies [text] to the clipboard when broadcast to this receiver.
         */
        fun getIntent(
            context: Context,
            text: TruncatedString,
        ): Intent =
            Intent(context, CopyToClipboardReceiver::class.java).apply {
                putExtra(EXTRA_SYNC_ERROR_LOG, text.value)
            }
    }
}
