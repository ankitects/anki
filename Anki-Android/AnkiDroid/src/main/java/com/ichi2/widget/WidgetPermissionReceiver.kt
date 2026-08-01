/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.common.android.AnkiBroadcastReceiver

/**
 * BroadcastReceiver to handle the scenario where storage permissions are granted,
 * triggering an update for widgets using the AddNoteWidget class.
 */
class WidgetPermissionReceiver : AnkiBroadcastReceiver() {
    override fun onReceiveBroadcast(
        context: Context,
        intent: Intent,
    ) {
        if (IntentHandler.grantedStoragePermissions(context, showToast = false)) {
            val appWidgetManager = getAppWidgetManager(context) ?: return
            val widgetIds = appWidgetManager.getAppWidgetIdsEx(ComponentName(context, AddNoteWidget::class.java))
            AddNoteWidget.updateWidgets(context, appWidgetManager, widgetIds)
        }
    }
}
