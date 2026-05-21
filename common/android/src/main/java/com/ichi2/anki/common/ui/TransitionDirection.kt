// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.ui

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Direction of an Activity transition animation.
 *
 * Consumed by `ActivityTransitionAnimation` (AnkiDroid app module) to choose
 * the appropriate slide/fade animation. Lives here so navigation destinations
 * in lower modules can pass a direction across the module boundary.
 */
@Parcelize
enum class TransitionDirection : Parcelable {
    START,
    END,
    FADE,
    UP,
    DOWN,
    RIGHT,
    LEFT,
    DEFAULT,
    NONE,
    ;

    /**
     * @return the inverse of this direction (e.g. [LEFT] <-> [RIGHT]), or this
     * direction unchanged when there is no meaningful inverse (e.g. [FADE]).
     */
    fun invert(): TransitionDirection =
        when (this) {
            // Directional transitions which should return their opposites
            RIGHT -> LEFT
            LEFT -> RIGHT
            UP -> DOWN
            DOWN -> UP
            START -> END
            END -> START
            // Non-directional transitions which should return themselves
            FADE -> FADE
            DEFAULT -> DEFAULT
            NONE -> NONE
        }
}
