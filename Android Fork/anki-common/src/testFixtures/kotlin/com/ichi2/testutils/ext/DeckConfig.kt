// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.ext

import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Consts

/** The preset's new-card limit for the default deck. */
val Collection.defaultDeckNewCardsPerDay: Int
    get() {
        val data = backend.getDeckConfigsForUpdate(Consts.DEFAULT_DECK_ID)
        return data.allConfigList
            .single { it.config.id == data.currentDeck.configId }
            .config.config.newPerDay
    }
