/*
 * Copyright (c) 2012 Norbert Nagold <norbert.nagold@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.receiver

import android.content.Context
import android.content.Intent
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.common.android.AnkiBroadcastReceiver
import timber.log.Timber

/**
 * This Broadcast-Receiver listens to media ejects and closes the collection prior to unmount. It then sends a broadcast
 * intent to all activities which might be open in order to show an appropriate screen After media has been remounted,
 * another broadcast intent will be sent to let the activities know about it
 */
class SdCardReceiver : AnkiBroadcastReceiver() {
    override fun onReceiveBroadcast(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_MEDIA_EJECT) {
            Timber.i("media eject detected - closing collection and sending broadcast")
            val i = Intent()
            i.action = MEDIA_EJECT
            context.sendBroadcast(i)
            try {
                val col = CollectionManager.getColUnsafe()
                col.close()
            } catch (e: Exception) {
                Timber.w(e, "Exception while trying to close collection likely because it was already unmounted")
            }
        } else if (intent.action == Intent.ACTION_MEDIA_MOUNTED) {
            Timber.i("media mount detected - sending broadcast")
            val i = Intent()
            i.action = MEDIA_MOUNT
            context.sendBroadcast(i)
        }
    }

    companion object {
        const val MEDIA_EJECT = "com.ichi2.anki.action.MEDIA_EJECT"
        const val MEDIA_MOUNT = "com.ichi2.anki.action.MEDIA_MOUNT"
    }
}
