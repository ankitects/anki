// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
package com.ichi2.widget.cardanalysis

import android.content.Context
import androidx.core.content.edit
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks.Companion.NOT_FOUND_DECK_ID
import com.ichi2.widget.AppWidgetId

class CardAnalysisWidgetPreferences(
    context: Context,
) {
    /**
     * Prefix for the SharedPreferences key used to store the selected deck for the Card Analysis Widget.
     * The full key is constructed by appending the appWidgetId to this prefix, ensuring that each
     * widget instance has a unique key. This approach helps prevent typos and ensures consistency
     * across the codebase when accessing or modifying the stored deck selections.
     */

    private val cardAnalysisWidgetSharedPreferences = context.getSharedPreferences("CardAnalysisExtraWidgetPrefs", Context.MODE_PRIVATE)

    /**
     * Deletes the selected deck ID from the shared preferences for the given widget ID.
     */
    fun deleteDeckData(appWidgetId: AppWidgetId) {
        cardAnalysisWidgetSharedPreferences.edit {
            remove(getCardAnalysisExtraWidgetKey(appWidgetId))
        }
    }

    fun getSelectedDeckIdFromPreferences(appWidgetId: AppWidgetId): DeckId? {
        val selectedDeckString =
            cardAnalysisWidgetSharedPreferences.getLong(
                getCardAnalysisExtraWidgetKey(appWidgetId),
                NOT_FOUND_DECK_ID,
            )
        return selectedDeckString.takeIf { it != NOT_FOUND_DECK_ID }
    }

    fun saveSelectedDeck(
        appWidgetId: AppWidgetId,
        selectedDeck: DeckId?,
    ) {
        cardAnalysisWidgetSharedPreferences.edit {
            putLong(getCardAnalysisExtraWidgetKey(appWidgetId), selectedDeck ?: NOT_FOUND_DECK_ID)
        }
    }
}

/**
 * Generates the key for the shared preferences for the given widget ID.
 */
private fun getCardAnalysisExtraWidgetKey(appWidgetId: AppWidgetId): String = "card_analysis_extra_widget_selected_deck_$appWidgetId"
