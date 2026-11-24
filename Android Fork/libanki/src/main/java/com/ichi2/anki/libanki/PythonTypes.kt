// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

/**
 * Identifier of a [Deck].
 *
 * The default deck has an ID of [1][Consts.DEFAULT_DECK_ID] (`Consts.DEFAULT_DECK_ID`)
 *
 * User-created decks use the deck creation time [(ms since the Unix epoch)][EpochMilliseconds]
 *
 * **Examples**
 * * `1428219222352` -> Deck created at 5 April 2015 07:33:42.352
 * * `1` -> Default Deck
 *
 * @see EpochMilliseconds
 * @see Deck.id
 */
typealias DeckId = EpochMilliseconds

/** Identifier of a [Card] */
typealias CardId = Long

/** Identifier of a [DeckConfig] */
typealias DeckConfigId = Long

/** Identifier of a [Note] */
typealias NoteId = Long

/** Identifier of a [Note Type][NotetypeJson] */
typealias NoteTypeId = Long

/**
 * The number of non-leap seconds which have elapsed since the
 * [Unix Epoch](https://en.wikipedia.org/wiki/Unix_time) (00:00:00 UTC on 1 January 1970)
 *
 * See: [https://www.epochconverter.com/](https://www.epochconverter.com/)
 *
 * example: 6 February 2024 19:15:49 -> `1707246949`
 */
typealias EpochSeconds = Long

/**
 * The number of non-leap milliseconds which have elapsed since the
 * [Unix Epoch](https://en.wikipedia.org/wiki/Unix_time) (00:00:00 UTC on 1 January 1970)
 *
 * See: [https://www.epochconverter.com/](https://www.epochconverter.com/)
 *
 * example: 5 April 2015 07:33:42.352 -> `1428219222352`
 */
typealias EpochMilliseconds = Long
