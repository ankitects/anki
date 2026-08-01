/*
 *  Copyright (c) 2021 Tyler Lewis <tyler.r.lewis1@gmail.com>
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

// This time, we're going to be doing the most that we can using ReadText.java. Rather than the reimplementation
// that happened with branch Cloze_TTS_#9590.
package com.ichi2.anki.cardviewer

import android.content.Context
import com.ichi2.anki.CardUtils
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.ReadText
import com.ichi2.anki.backend.stripHTML
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardOrdinal
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.TTSTag
import com.ichi2.anki.libanki.template.TemplateFilters
import com.ichi2.anki.provider.pureAnswer
import com.ichi2.anki.reviewer.CardSide

class TTS {
    @get:JvmName("isEnabled")
    var enabled: Boolean = false

    /**
     * Returns the card ordinal for TTS language storage.
     *
     * The ordinal of a Cloze card denotes the cloze deletion, causing the TTS
     * language to be requested and stored on every new highest cloze deletion when
     * used normally.
     *
     * @param card The card to check the type of before determining the ordinal.
     * @return The card ordinal. If it's a Cloze card, returns 0.
     */
    private fun getOrdUsingCardType(
        card: Card,
        col: Collection,
    ): CardOrdinal =
        if (card.noteType(col).isCloze) {
            0
        } else {
            card.ord
        }

    /**
     * Reads the text (using TTS) for the given side of a card.
     *
     * @param card     The card to play TTS for
     * @param cardSide The side of the current card to play TTS for
     */
    fun readCardText(
        col: Collection,
        ttsTags: List<TTSTag>,
        card: Card,
        cardSide: CardSide,
    ) {
        ReadText.readCardSide(ttsTags, cardSide, CardUtils.getDeckIdForCard(card), getOrdUsingCardType(card, col))
    }

    /**
     * Ask the user what language they want.
     *
     * @param card The card to read text from
     * @param qa   The card question or card answer
     */
    fun selectTts(
        col: Collection,
        card: Card,
        qa: CardSide,
    ) {
        val textToRead = if (qa == CardSide.QUESTION) card.question(col, true) else card.pureAnswer(col)
        // get the text from the card
        ReadText.selectTts(
            getTextForTts(textToRead),
            CardUtils.getDeckIdForCard(card),
            getOrdUsingCardType(card, col),
            qa,
        )
    }

    private fun getTextForTts(text: String): String {
        val clozeReplaced = text.replace(TemplateFilters.CLOZE_DELETION_REPLACEMENT, TR.cardTemplatesBlank())
        return stripHTML(clozeReplaced)
    }

    fun initialize(
        ctx: Context,
        listener: ReadText.ReadTextListener,
    ) {
        if (!enabled) {
            return
        }
        ReadText.initializeTts(ctx, listener)
    }

    /**
     * Request that TextToSpeech is stopped and shutdown after it it no longer being used
     * by the context that initialized it.
     * No-op if the current instance of TextToSpeech was initialized by another Context
     * @param context The context used during [ReadText.initializeTts]
     */
    fun releaseTts(context: Context) {
        if (!enabled) {
            return
        }
        ReadText.releaseTts(context)
    }
}
