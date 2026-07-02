/*
 *  Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>
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
