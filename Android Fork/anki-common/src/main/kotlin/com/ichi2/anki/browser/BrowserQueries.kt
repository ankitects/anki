// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.annotation.CheckResult
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.Utils.ids2str

// TODO: Move to :browse.

/**
 * Returns each note's lowest-ordinal card in one query, preserving [nids] order.
 *
 * For example, with these cards, `[101]` is returned. Ordinal 0 is not reqired.
 *
 * | Card ID | Ordinal |
 * |---------|---------|
 * | 100     | 2       |
 * | 101     | 1       |
 *
 * `firstCardIdsOfNotes(listOf(10))` returns `[101]`, the card with the lowest ordinal.
 *
 * @throws [NoSuchElementException] if any requested note does not exist or has no cards.
 */
@CheckResult
fun Collection.firstCardIdsOfNotes(nids: List<NoteId>): List<CardId> {
    if (nids.isEmpty()) return emptyList()
    val firstCards = mutableMapOf<NoteId, CardId>()
    db
        .query(
            """
            select cards.nid, cards.id from cards
            join (
                select nid, min(ord) as ord from cards
                where nid in ${ids2str(nids)} group by nid
            ) first_cards using (nid, ord)
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                firstCards[cursor.getLong(0)] = cursor.getLong(1)
            }
        }
    return nids.map { firstCards.getValue(it) }
}
