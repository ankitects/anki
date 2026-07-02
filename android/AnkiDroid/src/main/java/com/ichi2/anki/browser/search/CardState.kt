/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser.search

import anki.search.SearchNode
import anki.search.searchNode
import com.ichi2.anki.CollectionManager.TR
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Available card states to select for searches.
 *
 * Represents a subset of available values in an `is:` search. Similar to [SearchNode.CardState].
 *
 * Excluding:
 * - `is:due` - in [SearchNode.CardState], but not visible in the UI
 * - `is:buried-sibling` - not in `SearchNode.CardState`
 * - `is:buried-manually` - not in `SearchNode.CardState`
 *
 * **Documentation**
 *
 * - [Anki Protobuf (permalink)](https://github.com/ankitects/anki/blob/b8884bac72aa50fa1189fe0a5079a71574bc5043/proto/anki/search.proto#L58-L65)
 * - [Anki Manual](https://docs.ankiweb.net/searching.html#card-state)
 *
 * @param code Used for serialization, do not update.
 */
@Serializable(with = CardStateSerializer::class)
enum class CardState(
    val code: Int,
) {
    New(0),
    Learning(1),
    Review(2),

    // we deviate from Anki Desktop ordering here:
    // the logical progression of card states is is 'bury', then 'suspend'
    Buried(3),
    Suspended(4),
    ;

    /**
     * Internationalized, human readable label for the card state
     *
     * `CardState.New` => `"New"`
     */
    val label: String
        get() =
            when (this) {
                New -> TR.statisticsCountsNewCards()
                Learning -> TR.statisticsCountsLearningCards()
                Review -> TR.browsingSidebarCardStateReview()
                Buried -> TR.statisticsCountsBuriedCards()
                Suspended -> TR.browsingSuspended()
            }

    /**
     * Creates a [SearchNode.CardState] for use in a [SearchNode].
     *
     * Prefer [toSearchNode] for simple [SearchNode] creation.
     *
     * ```kotlin
     * val review = CardState.Review
     * val node = searchNode { cardState = review.toSearchValue() }
     * ```
     */
    fun toSearchValue(): SearchNode.CardState =
        when (this) {
            New -> SearchNode.CardState.CARD_STATE_NEW
            Learning -> SearchNode.CardState.CARD_STATE_LEARN
            Review -> SearchNode.CardState.CARD_STATE_REVIEW
            Buried -> SearchNode.CardState.CARD_STATE_BURIED
            Suspended -> SearchNode.CardState.CARD_STATE_SUSPENDED
        }

    /**
     * Creates a [SearchNode] for building a search string.
     *
     * Use [toSearchValue] when generating a complex `SearchNode`
     *
     * ```kotlin
     * val searchNode = CardState.Review.toSearchNode()
     * val searchString = col.buildSearchString(listOf(searchNode))
     * ```
     */
    fun toSearchNode(): SearchNode = searchNode { cardState = toSearchValue() }

    companion object {
        fun fromCode(id: Int): CardState = CardState.entries.first { it.code == id }
    }
}

/** Serializer for [CardState] */
object CardStateSerializer : KSerializer<CardState> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CardState", PrimitiveKind.INT)

    override fun serialize(
        encoder: Encoder,
        value: CardState,
    ) = encoder.encodeInt(value.code)

    override fun deserialize(decoder: Decoder): CardState {
        val code = decoder.decodeInt()
        return CardState.fromCode(code)
    }
}
