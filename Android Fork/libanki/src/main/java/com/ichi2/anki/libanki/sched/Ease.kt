// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki.sched

/**
 * [value] should be kept in sync with the [com.ichi2.anki.api.Ease] enum.
 *
 * @param value The so called value of the button. For the sake of consistency with upstream and our API
 * the buttons are numbered from 1 to 4.
 */
@Deprecated("use CardAnswer.Rating")
enum class Ease(
    val value: Int,
) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4),
    ;

    companion object {
        fun fromValue(value: Int) = entries.first { value == it.value }
    }
}
