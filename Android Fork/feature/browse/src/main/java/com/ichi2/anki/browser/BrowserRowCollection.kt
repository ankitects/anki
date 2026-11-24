// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.libanki.notesOfCards
import com.ichi2.anki.model.CardsOrNotes

/**
 * Collection of ids, either [CardId]s or [NoteId]s depending on the value of [cardsOrNotes].
 *
 * [net.ankiweb.rsdroid.Backend.browserRowForId]'s functionality depends on:
 * * [anki.config.ConfigKey.Bool.BROWSER_TABLE_SHOW_NOTES_MODE]
 * * [net.ankiweb.rsdroid.Backend.setActiveBrowserColumns]
 *
 * @see CardsOrNotes
 */
class BrowserRowCollection(
    private var cardsOrNotes: CardsOrNotes,
    private val cardOrNoteIdList: MutableList<CardOrNoteId>,
) : MutableList<CardOrNoteId> by cardOrNoteIdList {
    fun replaceWith(
        cardsOrNotes: CardsOrNotes,
        newList: List<CardOrNoteId>,
    ) {
        this.cardsOrNotes = cardsOrNotes
        cardOrNoteIdList.clear()
        cardOrNoteIdList.addAll(newList)
    }

    fun reset() {
        cardOrNoteIdList.clear()
    }

    suspend fun queryNoteIds(): List<NoteId> =
        when (this.cardsOrNotes) {
            CardsOrNotes.NOTES -> requireNoteIdList()
            CardsOrNotes.CARDS -> CollectionManager.withCol { notesOfCards(cids = requireCardIdList()) }
        }

    suspend fun queryCardIds(): List<CardId> =
        when (this.cardsOrNotes) {
            // TODO: This is slower than necessary and not perform SQL queries in a loop
            CardsOrNotes.NOTES ->
                requireNoteIdList().flatMap { nid ->
                    CollectionManager.withCol {
                        cardIdsOfNote(
                            nid = nid,
                        )
                    }
                }
            CardsOrNotes.CARDS -> requireCardIdList()
        }

    suspend fun queryOneCardIdPerRow(): List<CardId> =
        when (this.cardsOrNotes) {
            CardsOrNotes.NOTES -> {
                val noteIds = requireNoteIdList()
                CollectionManager.withCol { firstCardIdsOfNotes(noteIds) }
            }
            CardsOrNotes.CARDS -> requireCardIdList()
        }

    private fun requireNoteIdList(): List<NoteId> {
        require(cardsOrNotes == CardsOrNotes.NOTES)
        return cardOrNoteIdList.map { it.cardOrNoteId }
    }

    private fun requireCardIdList(): List<CardId> {
        require(cardsOrNotes == CardsOrNotes.CARDS)
        return cardOrNoteIdList.map { it.cardOrNoteId }
    }

    suspend fun queryCardIdsAt(position: Int): List<CardId> =
        when (this.cardsOrNotes) {
            CardsOrNotes.NOTES -> CollectionManager.withCol { cardIdsOfNote(nid = cardOrNoteIdList[position].cardOrNoteId) }
            CardsOrNotes.CARDS -> listOf(cardOrNoteIdList[position].cardOrNoteId)
        }
}

// PERF: We can do better here, just done for abstraction
suspend fun Collection<CardOrNoteId>.queryNoteIds(cardsOrNotes: CardsOrNotes): List<NoteId> =
    BrowserRowCollection(cardsOrNotes, this.toMutableList()).queryNoteIds()

suspend fun Collection<CardOrNoteId>.queryCardIds(cardsOrNotes: CardsOrNotes): List<CardId> =
    BrowserRowCollection(cardsOrNotes, this.toMutableList()).queryCardIds()
