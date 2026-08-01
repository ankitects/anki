// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.destinations.CardInfoDestination
import com.ichi2.anki.common.destinations.CardInfoDestination.EntryPoint
import com.ichi2.anki.libanki.withoutUnicodeIsolation
import com.ichi2.anki.ui.internationalization.sentenceCase

/** Builds the [Intent] that opens the card info screen for this destination. */
fun CardInfoDestination.toIntent(context: Context): Intent {
    val title = entryPoint.toCardInfoTitle(context)
    val arguments =
        Bundle().apply {
            putString(CardInfoFragment.KEY_TITLE, title)
            putLong(CardInfoFragment.KEY_CARD_ID, cardId)
        }
    return SingleFragmentActivity.getIntent(
        context,
        fragmentClass = CardInfoFragment::class,
        arguments = arguments,
    )
}

/**
 * Converts a backend card-stats title to AnkiDroid's sentence-cased form when it matches the
 * entry point's sentence-case resource.
 */
// TODO: Move this into EntryPoint once sentenceCase is in anki-common
private fun EntryPoint.toCardInfoTitle(context: Context): String {
    val title = this.title(TR)
    // The title contains FSI and PDI character types - these need to be stripped for the comparison
    val simplifiedTitle = withoutUnicodeIsolation(title)
    val sentenceCased =
        with(context) {
            when (this@toCardInfoTitle) {
                EntryPoint.CURRENT_CARD_STUDY -> TR.sentenceCase.cardStatsCurrentCardStudy(simplifiedTitle)
                EntryPoint.CURRENT_CARD_BROWSE -> TR.sentenceCase.cardStatsCurrentCardBrowse(simplifiedTitle)
                EntryPoint.PREVIOUS_CARD_STUDY -> TR.sentenceCase.cardStatsPreviousCardStudy(simplifiedTitle)
            }
        }
    // Do not return the stripped title if there's no match
    return if (sentenceCased != simplifiedTitle) sentenceCased else title
}
