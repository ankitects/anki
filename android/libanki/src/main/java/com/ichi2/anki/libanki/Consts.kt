/*
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.libanki

import androidx.annotation.IntDef
import kotlin.annotation.Retention

// Card types
sealed class CardType(
    val code: Int,
) {
    data object New : CardType(0)

    data object Lrn : CardType(1)

    data object Rev : CardType(2)

    data object Relearning : CardType(3)

    class Unknown(
        code: Int,
    ) : CardType(code)

    companion object {
        fun fromCode(code: Int) =
            when (code) {
                0 -> New
                1 -> Lrn
                2 -> Rev
                3 -> Relearning
                else -> Unknown(code)
            }
    }
}

sealed class QueueType(
    val code: Int,
) {
    data object ManuallyBuried : QueueType(-3)

    data object SiblingBuried : QueueType(-2)

    data object Suspended : QueueType(-1)

    data object New : QueueType(0)

    data object Lrn : QueueType(1)

    data object Rev : QueueType(2)

    data object DayLearnRelearn : QueueType(3)

    data object Preview : QueueType(4)

    class Unknown(
        code: Int,
    ) : QueueType(code)

    /**
     * Whether this card can be reviewed.
     */
    fun buriedOrSuspended() =
        when (this) {
            ManuallyBuried, SiblingBuried, Suspended -> true
            New, Lrn, Rev, DayLearnRelearn, Preview -> false
            is Unknown -> this.code < 0
        }

    companion object {
        fun fromCode(code: Int): QueueType =
            when (code) {
                -3 -> ManuallyBuried
                -2 -> SiblingBuried
                -1 -> Suspended
                0 -> New
                1 -> Lrn
                2 -> Rev
                3 -> DayLearnRelearn
                else -> Unknown(code)
            }
    }
}

// model types
sealed class NoteTypeKind(
    val code: Int,
) {
    data object Std : NoteTypeKind(0)

    data object Cloze : NoteTypeKind(1)

    class Unknown(
        code: Int,
    ) : NoteTypeKind(code)

    companion object {
        fun fromCode(code: Int) =
            when (code) {
                0 -> Std
                1 -> Cloze
                else -> Unknown(code)
            }
    }
}

object Consts {
    // dynamic deck order
    const val DYN_OLDEST = 0
    const val DYN_RANDOM = 1
    const val DYN_SMALLINT = 2
    const val DYN_BIGINT = 3
    const val DYN_LAPSES = 4
    const val DYN_ADDED = 5
    const val DYN_DUE = 6
    const val DYN_REVADDED = 7
    const val DYN_DUEPRIORITY = 8
    const val DYN_MAX_SIZE = 99999

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(DYN_OLDEST, DYN_RANDOM, DYN_SMALLINT, DYN_BIGINT, DYN_LAPSES, DYN_ADDED, DYN_DUE, DYN_REVADDED, DYN_DUEPRIORITY)
    annotation class DynPriority

    const val STARTING_FACTOR = 2500

    /** Only used by the dialog shown to user */
    const val BACKEND_SCHEMA_VERSION = 18

    const val SYNC_VER = 10

    // Leech actions
    const val LEECH_SUSPEND = 0

    // The labels defined in consts.py are in AnkiDroid's resources files.
    const val DEFAULT_DECK_ID: DeckId = 1L

    const val FIELD_SEPARATOR = "${'\u001f'}"

    /** Time duration for toast **/
    const val SHORT_TOAST_DURATION: Long = 2000
}
