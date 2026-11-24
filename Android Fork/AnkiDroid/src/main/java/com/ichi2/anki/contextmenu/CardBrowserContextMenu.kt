// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.contextmenu

import android.content.Context

class CardBrowserContextMenu(
    context: Context,
) : SystemContextMenu(context) {
    override val activityName: String
        get() = "com.ichi2.anki.CardBrowserContextMenuAction"

    companion object {
        fun ensureConsistentStateWithPreferenceStatus(
            context: Context,
            preferenceStatus: Boolean,
        ) {
            CardBrowserContextMenu(context).ensureConsistentStateWithPreferenceStatus(preferenceStatus)
        }
    }
}
