/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.ichi2.widget.cardanalysis.CardAnalysisWidget
import com.ichi2.widget.deckpicker.DeckPickerWidget

/**
 * @return An [AppWidgetManager] for the provided context, or `null`
 *
 * @see AppWidgetManager.getInstance
 */

fun getAppWidgetManager(context: Context): AppWidgetManager? {
    // The call returns null on a Supernote A5X, but as the underlying platform call is in Java,
    // the result is assumed to be non-null in Kotlin
    return AppWidgetManager.getInstance(context)
}

/** Whether 'Material You' dynamic color should be used for widgets */
val disableMaterialYouDynamicColor: Boolean
    get() = true

val RECURRING_WIDGETS =
    listOf(
        DeckPickerWidget::class.java,
        CardAnalysisWidget::class.java,
    )

val NON_RECURRING_WIDGETS =
    listOf(
        AddNoteWidget::class.java,
        AnkiDroidWidgetSmall::class.java,
    )
