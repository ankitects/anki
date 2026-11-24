// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import com.ichi2.anki.reviewer.CardSide

enum class SingleCardSide {
    FRONT,
    BACK,
    ;

    fun toCardSide(): CardSide =
        when (this) {
            FRONT -> CardSide.QUESTION
            BACK -> CardSide.ANSWER
        }
}
