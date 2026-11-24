// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.os.Parcelable
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.model.CardsOrNotes
import kotlinx.parcelize.Parcelize

/**
 * Either a [CardId] or a [NoteId]. The ID of a row inside the 'Browser' can be either.
 *
 * A [CardOrNoteId] should always be associated with [CardsOrNotes].
 * It is not included in the class as this class is primarily provided in a [BrowserRowCollection]
 * and storing the same value for every item in the list is a waste
 */
@Parcelize
@JvmInline
value class CardOrNoteId(
    val cardOrNoteId: Long,
) : Parcelable {
    override fun toString(): String = cardOrNoteId.toString()

    // TODO: We use this for 'Edit Note' or 'Card Info'. We should reconsider whether we ever want
    //  to move from NoteId to CardId. Our move to 'Notes' mode wasn't well thought-through

    // TODO: Notes without cards likely indicate an invalid or corrupted collection state.
    //  We currently handle this gracefully by returning an empty list,
    //  we may want to surface this as a warning or integrity check for the user.
    suspend fun toCardId(type: CardsOrNotes): CardId? =
        when (type) {
            CardsOrNotes.CARDS -> cardOrNoteId
            // A note can map to multiple cards or none at all.
            // See [cardIdsOfNote] for the full explanation and edge cases
            // (empty templates, orphaned notes, etc).
            CardsOrNotes.NOTES -> withCol { cardIdsOfNote(cardOrNoteId).firstOrNull() }
        }

    companion object {
        fun fromCard(
            card: Card,
            cardsOrNotes: CardsOrNotes,
        ): CardOrNoteId = CardOrNoteId(if (cardsOrNotes == CardsOrNotes.CARDS) card.id else card.nid)
    }
}
