/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.withoutUnicodeIsolation
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.Destination

data class CardInfoDestination(
    val cardId: CardId,
    val title: String,
) : Destination {
    override fun toIntent(context: Context): Intent {
        // title contains FSI and PDI character types
        val simplifiedTitle = withoutUnicodeIsolation(title)
        val sentenceStrings =
            listOf(
                simplifiedTitle.toSentenceCase(context, R.string.sentence_card_stats_current_card_study),
                simplifiedTitle.toSentenceCase(context, R.string.sentence_card_stats_current_card_browse),
                simplifiedTitle.toSentenceCase(context, R.string.sentence_card_stats_previous_card_study),
            )
        val cardInfoTitle = sentenceStrings.firstOrNull { it != simplifiedTitle } ?: title
        val arguments =
            Bundle().apply {
                putString(CardInfoFragment.KEY_TITLE, cardInfoTitle)
                putLong(CardInfoFragment.KEY_CARD_ID, cardId)
            }
        return SingleFragmentActivity.getIntent(
            context,
            fragmentClass = CardInfoFragment::class,
            arguments = arguments,
        )
    }
}
