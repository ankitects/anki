/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  This file incorporates code under the following license
 *  https://github.com/ankitects/anki/blob/9600f033f745bfae4e00dd9fa43e44d3b30c22d2/qt/aqt/tts.py
 *
 *    Copyright: Ankitects Pty Ltd and contributors
 *    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */

package com.ichi2.anki.libanki

import java.io.Closeable

open class TtsVoice(
    val name: String,
    val lang: String,
) {
    override fun toString(): String {
        var out = "{{tts $lang voices=$name}}"
        if (unavailable()) {
            out += " (unavailable)"
        }
        return out
    }

    open fun unavailable(): Boolean = false
}

data class TtsVoiceMatch(
    val voice: TtsVoice,
    val rank: Int,
)

abstract class TtsPlayer : Closeable {
    open val defaultRank = 0

    private var availableVoices: List<TtsVoice>? = null

    abstract fun getAvailableVoices(): List<TtsVoice>

    abstract class TtsError

    data class TtsCompletionStatus(
        val success: Boolean?,
        val error: TtsError? = null,
    ) {
        companion object {
            fun success() = TtsCompletionStatus(success = true)

            fun stopped() = TtsCompletionStatus(success = null)

            fun failure(errorCode: TtsError) = TtsCompletionStatus(success = false, errorCode)
        }
    }

    // libanki uses `AvTag` as `tag`'s parameter type, but it requires `TTSTag` anyway
    // changed here to get advantage of static typing
    abstract suspend fun play(tag: TTSTag): TtsCompletionStatus

    fun voices(): List<TtsVoice> {
        if (availableVoices == null) {
            availableVoices = getAvailableVoices()
        }
        return availableVoices!!
    }

    fun voiceForTag(tag: TTSTag): TtsVoiceMatch? {
        val availVoices = voices()

        var rank = defaultRank

        // any requested voices match?
        for (requestedVoice in tag.voices) {
            for (avail in availVoices) {
                if (avail.name == requestedVoice && avail.lang == tag.lang) {
                    return TtsVoiceMatch(voice = avail, rank = rank)
                }
            }
            rank -= 1
        }

        // if no preferred voices match, we fall back on language
        // with a rank of -100
        for (avail in availVoices) {
            if (avail.lang == tag.lang) {
                return TtsVoiceMatch(voice = avail, rank = -100)
            }
        }
        return null
    }
}
