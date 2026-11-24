// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

package com.ichi2.widget.deckpicker

import android.content.Context
import androidx.core.content.edit
import com.ichi2.widget.AppWidgetId

class DeckPickerWidgetPreferences(
    context: Context,
) {
    /**
     * Prefix for the SharedPreferences key used to store the selected decks for the DeckPickerWidget.
     * The full key is constructed by appending the appWidgetId to this prefix, ensuring that each
     * widget instance has a unique key. This approach helps prevent typos and ensures consistency
     * across the codebase when accessing or modifying the stored deck selections.
     */

    private val deckPickerSharedPreferences = context.getSharedPreferences("DeckPickerWidgetPrefs", Context.MODE_PRIVATE)

    /**
     * Deletes the selected deck IDs from the shared preferences for the given widget ID.
     */
    fun deleteDeckData(appWidgetId: AppWidgetId) {
        deckPickerSharedPreferences.edit {
            remove(getDeckPickerWidgetKey(appWidgetId))
        }
    }

    /**
     * Retrieves the selected deck IDs from the shared preferences for the given widget ID.
     * Note: There's no guarantee that these IDs still represent decks that exist at the time of execution.
     */
    fun getSelectedDeckIdsFromPreferences(appWidgetId: AppWidgetId): LongArray {
        val selectedDecksString = deckPickerSharedPreferences.getString(getDeckPickerWidgetKey(appWidgetId), "")
        return if (!selectedDecksString.isNullOrEmpty()) {
            selectedDecksString.split(",").map { it.toLong() }.toLongArray()
        } else {
            longArrayOf()
        }
    }

    /**
     * Saves the selected deck IDs to the shared preferences for the given widget ID.
     */
    fun saveSelectedDecks(
        appWidgetId: AppWidgetId,
        selectedDecks: List<String>,
    ) {
        deckPickerSharedPreferences.edit {
            putString(getDeckPickerWidgetKey(appWidgetId), selectedDecks.joinToString(","))
        }
    }
}

/**
 * Generates the key for the shared preferences for the given widget ID.
 */
private fun getDeckPickerWidgetKey(appWidgetId: AppWidgetId): String = "deck_picker_widget_selected_decks_$appWidgetId"
