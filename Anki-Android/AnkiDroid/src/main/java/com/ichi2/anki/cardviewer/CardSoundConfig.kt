/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.cardviewer

import androidx.annotation.CheckResult
import com.ichi2.anki.CardUtils
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import timber.log.Timber

/**
 * The options for playing sound for a given card
 *
 * @param replayQuestion deck option: "Skip question when replaying answer".
 * `true`: replay the question and the answer
 * `false`: only replay the answer
 * @param autoplay deck option: "Don't play audio automatically"
 */
class CardSoundConfig(
    val replayQuestion: Boolean,
    val autoplay: Boolean,
    val deckId: DeckId,
) {
    // PERF: technically, we can go further with options groups
    @CheckResult
    fun appliesTo(card: Card): Boolean = CardUtils.getDeckIdForCard(card) == deckId

    companion object {
        @CheckResult
        fun create(
            col: Collection,
            card: Card,
        ): CardSoundConfig {
            Timber.v("start loading SoundConfig")

            val autoPlay = card.autoplay(col)

            val replayQuestion: Boolean = card.replayQuestionAudioOnAnswerSide(col)

            return CardSoundConfig(replayQuestion, autoPlay, card.did).apply {
                Timber.d("loaded SoundConfig: %s", this)
            }
        }
    }
}
