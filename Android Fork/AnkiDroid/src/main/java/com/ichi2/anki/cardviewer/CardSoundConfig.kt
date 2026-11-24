// SPDX-License-Identifier: GPL-3.0-or-later

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
