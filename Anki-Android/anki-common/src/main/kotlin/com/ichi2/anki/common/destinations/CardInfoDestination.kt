// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.common.destinations

import com.ichi2.anki.libanki.CardId
import net.ankiweb.rsdroid.Translations

/**
 * Opens the Card Info screen for [cardId]. [entryPoint] defines the backend-provided title.
 */
data class CardInfoDestination(
    val cardId: CardId,
    val entryPoint: EntryPoint,
) : Destination() {
    /**
     * Where the Card Info screen was opened from. Used to obtain the screen title.
     */
    enum class EntryPoint(
        val title: Translations.() -> String,
    ) {
        /** Current Card (Study) */
        CURRENT_CARD_STUDY({ cardStatsCurrentCard(decksStudy()) }),

        /** Current Card (Browse) */
        CURRENT_CARD_BROWSE({ cardStatsCurrentCard(qtMiscBrowse()) }),

        /** Previous Card (Study) */
        PREVIOUS_CARD_STUDY({ cardStatsPreviousCard(decksStudy()) }),
    }
}
