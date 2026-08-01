/*
 * Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.utils.ext

import androidx.annotation.CheckResult
import anki.collection.OpChangesWithCount
import anki.config.ConfigKey
import anki.search.SearchNode
import com.ichi2.anki.Flag
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.Collection

/** Change the flag color of the specified cards. */
@CheckResult
fun Collection.setUserFlagForCards(
    cids: Iterable<Long>,
    flag: Flag,
): OpChangesWithCount = setUserFlagForCards(cids, flag.code)

/**
 * The `Custom scheduling` global setting in deck options.
 */
var Collection.cardStateCustomizer: String
    get() = config.getString(ConfigKey.String.CARD_STATE_CUSTOMIZER)
    set(value) {
        config.setString(ConfigKey.String.CARD_STATE_CUSTOMIZER, value)
    }

/** @see Collection.getCard */
fun Collection.getCardOrNull(id: CardId): Card? = runCatching { getCard(id) }.getOrNull()

/**
 * Constructs a search string from a [SearchNode].
 *
 * @see Collection.buildSearchString
 */
fun Collection.buildSearchString(node: SearchNode): String = buildSearchString(listOf(node))
