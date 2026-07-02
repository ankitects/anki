// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 William-2357 <William-2357@users.noreply.github.com>

package com.ichi2.anki.pages

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.DeckId

/**
 * CFA Speedrun: read-only knowledge map for a deck. Hosts the shared `concept-graph`
 * SvelteKit page, which calls the backend `GetConceptGraph` RPC (clusters by tag +
 * co-occurrence edges + mean FSRS retrievability). See `ANDROID_PORTING.md`.
 */
class ConceptGraph : PageFragment() {
    override val pagePath: String by lazy {
        "concept-graph/${requireArguments().getLong(KEY_DECK_ID)}"
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val deckId = requireArguments().getLong(KEY_DECK_ID)
        launchCatchingTask {
            val name = withCol { decks.name(deckId, default = true) }
            view.findViewById<MaterialToolbar>(R.id.toolbar)?.title = name
        }
    }

    companion object {
        private const val KEY_DECK_ID = "deckId"

        fun getIntent(
            context: Context,
            deckId: DeckId,
        ): Intent =
            SingleFragmentActivity.getIntent(
                context,
                fragmentClass = ConceptGraph::class,
                arguments = Bundle().apply { putLong(KEY_DECK_ID, deckId) },
            )
    }
}
