/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.sync

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.R
import com.ichi2.anki.settings.Prefs
import com.ichi2.utils.NetworkUtils.isActiveNetworkMetered
import com.ichi2.utils.checkBoxPrompt
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show

/**
 * Single source of truth for the "warn the user before syncing on a metered connection"
 * preference. All reads of [Prefs.allowSyncOnMeteredConnections] should go through here
 * so that the gating policy lives in one place.
 */
object MeteredSyncPolicy {
    /** True when sync should be blocked/prompted because the network is metered. */
    fun shouldBlock(): Boolean = !Prefs.allowSyncOnMeteredConnections && isActiveNetworkMetered()

    /** Persist the user's "don't ask again" choice from the warning dialog. */
    fun setAlwaysAllow(allow: Boolean) {
        Prefs.allowSyncOnMeteredConnections = allow
    }

    /**
     * Run [onConfirm] immediately if the network is unmetered, metered-network sync is
     * allowed, or [skipPrompt] is set; otherwise show a warning dialog and run [onConfirm] when
     * 'Continue' is pressed.
     *
     * @param skipPrompt `true` if the user has already accepted a metered sync earlier in this
     *   attempt (e.g. retry after conflict resolution); skips the warning.
     * @param onDialogShown invoked only if the warning dialog is displayed
     */
    context(context: Context)
    fun confirmThen(
        skipPrompt: Boolean = false,
        onDialogShown: () -> Unit,
        onConfirm: () -> Unit,
    ) {
        if (skipPrompt || !shouldBlock()) {
            onConfirm()
            return
        }
        AlertDialog.Builder(context).show {
            message(R.string.metered_sync_data_warning)
            positiveButton(R.string.dialog_continue) { onConfirm() }
            negativeButton(R.string.dialog_cancel)
            checkBoxPrompt(R.string.button_do_not_show_again) { checked ->
                setAlwaysAllow(checked)
            }
        }
        onDialogShown()
    }
}
