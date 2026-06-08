// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.destinations.CardInfoDestination
import com.ichi2.anki.libanki.withoutUnicodeIsolation
import com.ichi2.anki.ui.internationalization.toSentenceCase

/** Builds the [Intent] that opens the card info screen for this destination. */
fun CardInfoDestination.toIntent(context: Context): Intent {
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
