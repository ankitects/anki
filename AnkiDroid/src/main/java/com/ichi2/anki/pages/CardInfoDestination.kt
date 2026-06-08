// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.StringRes
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.destinations.CardInfoDestination
import com.ichi2.anki.common.destinations.CardInfoDestination.EntryPoint
import com.ichi2.anki.libanki.withoutUnicodeIsolation
import com.ichi2.anki.ui.internationalization.toSentenceCase

/** Builds the [Intent] that opens the card info screen for this destination. */
fun CardInfoDestination.toIntent(context: Context): Intent {
    val title = entryPoint.title(TR).toCardInfoTitle(context, entryPoint.sentenceCaseResId)
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

/** The sentence case resource matching this entry point's backend [EntryPoint.title]. */
@get:StringRes
private val EntryPoint.sentenceCaseResId: Int
    get() =
        when (this) {
            EntryPoint.CURRENT_CARD_STUDY -> R.string.sentence_card_stats_current_card_study
            EntryPoint.CURRENT_CARD_BROWSE -> R.string.sentence_card_stats_current_card_browse
            EntryPoint.PREVIOUS_CARD_STUDY -> R.string.sentence_card_stats_previous_card_study
        }

/**
 * Converts a backend card-stats title to AnkiDroid's sentence-cased form when it matches [resId].
 *
 * @see toSentenceCase
 */
private fun String.toCardInfoTitle(
    context: Context,
    @StringRes resId: Int,
): String {
    // The title contains FSI and PDI character types - these need to be stripped for the comparison
    val simplifiedTitle = withoutUnicodeIsolation(this)
    val sentenceCased = simplifiedTitle.toSentenceCase(context, resId)
    // Do not return the stripped title if there's no match
    return if (sentenceCased != simplifiedTitle) sentenceCased else this
}
