// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.contextmenu

import android.content.Context

class AnkiCardContextMenu(
    context: Context,
) : SystemContextMenu(context) {
    override val activityName: String
        get() = "com.ichi2.anki.AnkiCardContextMenuAction"

    companion object {
        fun ensureConsistentStateWithPreferenceStatus(
            context: Context,
            preferenceStatus: Boolean,
        ) {
            AnkiCardContextMenu(context).ensureConsistentStateWithPreferenceStatus(preferenceStatus)
        }
    }
}
