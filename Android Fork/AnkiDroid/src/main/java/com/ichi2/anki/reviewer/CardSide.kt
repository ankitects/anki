// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

/**
 * @param int Used for serialisation
 */
enum class CardSide(
    val int: Int,
) {
    QUESTION(0),
    ANSWER(1),
    BOTH(2),
    ;

    companion object {
        fun fromAnswer(displayingAnswer: Boolean): CardSide = if (displayingAnswer) ANSWER else QUESTION
    }
}
