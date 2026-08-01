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

package com.ichi2.anki.model

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.cardviewer.SingleCardSide.FRONT

/**
 * Special fields allow a card template to use properties of the current card/note
 *
 * Example: `{{Subdeck}}` displays the deck name
 *
 * - [Anki Manual: Special fields](https://docs.ankiweb.net/templates/fields.html#special-fields)
 * - [Source (permalink)](https://github.com/ankitects/anki/blob/8f2144534bff6efedb22b7f052fba13ffe28cbc2/rslib/src/notetype/mod.rs#L70-L82)
 */
@JvmInline
value class SpecialField(
    val name: String,
)

/** @see SpecialField */
object SpecialFields {
    /**
     * The content of the front template. Only valid on the back template
     *
     * `FrontSide` does not automatically play any audio that was on the front side of the card
     */
    val FrontSide = SpecialField("FrontSide")

    /**
     * The name of the card template (`Card 1`)
     */
    val CardTemplate = SpecialField("Card")

    /**
     * The card's flag, including its integer code.
     *
     * `flagN` where N :
     * * 0 - unset
     * * 1 - RED etc...
     *
     * The integer part uses Arabic numerals (0-9) on all locales.
     *
     * @see com.ichi2.anki.Flag.code
     */
    val Flag = SpecialField("CardFlag")

    /**
     * The full tree of the card's deck
     *
     * `A::B:C`
     */
    val Deck = SpecialField("Deck")

    /**
     * The card's subdeck
     *
     * `C`, if the card is in deck: `A::B:C`
     */
    val Subdeck = SpecialField("Subdeck")

    /**
     * The note's tags
     *
     * space-delimited: `tag1 tag2`
     */
    val Tags = SpecialField("Tags")

    /**
     * The name of the note type
     *
     * example: `Basic`
     */
    val NoteType = SpecialField("Type")

    /** @see com.ichi2.anki.libanki.CardId */
    val CardId = SpecialField("CardID")

    @VisibleForTesting
    internal val ALL =
        listOf(
            FrontSide,
            Deck,
            Subdeck,
            Tags,
            Flag,
            NoteType,
            CardTemplate,
            CardId,
        )

    /**
     * Returns all available special fields in an order suitable for displaying to a user
     */
    fun all(side: SingleCardSide) =
        ALL.filter { field ->
            when {
                field == FrontSide && side == FRONT -> false
                else -> true
            }
        }
}
