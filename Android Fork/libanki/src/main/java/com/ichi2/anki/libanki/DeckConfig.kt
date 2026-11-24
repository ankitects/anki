// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Arthur Milchior <Arthur@Milchior.fr>

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
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(DeckConfig::class.java)

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
                logger.warn("Unable to read timer configuration as an integer", e)
                try {
                    jsonObject.getBoolean("timer")
                } catch (ex: Exception) {
                    logger.warn("Unable to read timer configuration as a boolean", ex)
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
