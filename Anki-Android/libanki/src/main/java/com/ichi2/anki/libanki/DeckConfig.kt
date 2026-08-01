/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 Copyright (c) 2020 Arthur Milchior <Arthur@Milchior.fr>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.libanki

import androidx.annotation.VisibleForTesting
import com.ichi2.anki.common.json.JSONObjectHolder
import com.ichi2.anki.common.json.jsonArray
import com.ichi2.anki.common.json.jsonBoolean
import com.ichi2.anki.common.json.jsonDouble
import com.ichi2.anki.common.json.jsonInt
import com.ichi2.anki.common.json.jsonLong
import com.ichi2.anki.common.json.jsonString
import org.intellij.lang.annotations.Language
import org.json.JSONObject
import timber.log.Timber

/**
 * Creates a copy from [JSONObject] and use it as a string
 *
 * The [jsonObject] parameter will be edited. Create a copy first if you need not to modify the parameter.
 *
 */
data class DeckConfig(
    @VisibleForTesting
    override val jsonObject: JSONObject,
) : JSONObjectHolder {
    constructor(
        @Language("JSON") json: String,
    ) : this(JSONObject(json))

    var conf by jsonLong("cong")

    var id: DeckConfigId by jsonLong("id")

    var name by jsonString("name")

    val waitForAudio by jsonBoolean("waitForAudio", defaultValue = true)

    @VisibleForTesting(VisibleForTesting.NONE)
    fun removeAnswerAction() {
        jsonObject.remove(ANSWER_ACTION)
    }

    val stopTimerOnAnswer by jsonBoolean("stopTimerOnAnswer")

    var secondsToShowQuestion by jsonDouble("secondsToShowQuestion", defaultValue = 0.0)

    var secondsToShowAnswer by jsonDouble("secondsToShowAnswer", defaultValue = 0.0)

    /**
     * Time limit for answering in milliseconds.
     */
    val maxTaken by jsonInt("maxTaken")

    @set:VisibleForTesting(VisibleForTesting.NONE)
    var autoplay by jsonBoolean("autoplay", defaultValue = true)

    @set:VisibleForTesting(VisibleForTesting.NONE)
    var replayq by jsonBoolean("replayq")

    val timer: Boolean
        get() =
            // Note: Card.py used != 0, DeckOptions used == 1
            try {
                // #6089 - Anki 2.1.24 changed this to a bool, reverted in 2.1.25.
                jsonObject.getInt("timer") != 0
            } catch (e: Exception) {
                Timber.w(e)
                try {
                    jsonObject.getBoolean("timer")
                } catch (ex: Exception) {
                    Timber.w(ex)
                    true
                }
            }

    val new: New
        get() = New(jsonObject.getJSONObject("new"))

    data class New(
        override val jsonObject: JSONObject,
    ) : JSONObjectHolder {
        var perDay by jsonInt("perDay")

        @VisibleForTesting
        var delays by jsonArray("delays")

        /**
         * Whether sibling of reviewed cards get buried.
         */
        @VisibleForTesting
        var bury by jsonBoolean("bury")
    }

    val lapse: Lapse
        get() = Lapse(jsonObject.getJSONObject("lapse"))

    data class Lapse(
        override val jsonObject: JSONObject,
    ) : JSONObjectHolder {
        @VisibleForTesting
        var delays by jsonArray("delays")

        @VisibleForTesting
        var leechAction by jsonInt("leechAction")

        @VisibleForTesting
        var mult by jsonDouble("mult")
    }

    val rev: Rev
        get() = Rev(jsonObject.getJSONObject("rev"))

    data class Rev(
        override val jsonObject: JSONObject,
    ) : JSONObjectHolder {
        @VisibleForTesting
        var perDay by jsonInt("perDay")

        @VisibleForTesting
        var delays by jsonArray("delays")

        @VisibleForTesting
        var hardFactor: Int by jsonInt("hardFactor")
    }

    override fun toString() = jsonObject.toString()

    companion object {
        const val QUESTION_ACTION = "questionAction"
        const val ANSWER_ACTION = "answerAction"
    }
}
