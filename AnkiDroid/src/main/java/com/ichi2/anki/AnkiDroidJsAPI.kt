/*
 * Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 * Copyright (c) 2020 Mani infinyte01@gmail.com
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
 * this program.  If not, see http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import anki.scheduler.CardAnswer.Rating
import com.github.zafarkhaja.semver.Version
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.AnkiDroidJsAPIConstants.ANKI_JS_ERROR_CODE_BURT_NOTE
import com.ichi2.anki.AnkiDroidJsAPIConstants.ANKI_JS_ERROR_CODE_BURY_CARD
import com.ichi2.anki.AnkiDroidJsAPIConstants.ANKI_JS_ERROR_CODE_ERROR
import com.ichi2.anki.AnkiDroidJsAPIConstants.ANKI_JS_ERROR_CODE_FLAG_CARD
import com.ichi2.anki.AnkiDroidJsAPIConstants.ANKI_JS_ERROR_CODE_MARK_CARD
import com.ichi2.anki.AnkiDroidJsAPIConstants.ANKI_JS_ERROR_CODE_SET_DUE
import com.ichi2.anki.AnkiDroidJsAPIConstants.ANKI_JS_ERROR_CODE_SUSPEND_CARD
import com.ichi2.anki.AnkiDroidJsAPIConstants.ANKI_JS_ERROR_CODE_SUSPEND_NOTE
import com.ichi2.anki.AnkiDroidJsAPIConstants.flagCommands
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.browser.search.SearchString
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.ext.stringIterable
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Decks
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.SortOrder
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.servicelayer.rescheduleCards
import com.ichi2.anki.servicelayer.resetCards
import com.ichi2.anki.snackbar.setMaxLines
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

typealias JvmBoolean = Boolean
typealias JvmInt = Int
typealias JvmFloat = Float
typealias JvmLong = Long
typealias JvmString = String

open class AnkiDroidJsAPI(
    private val activity: AbstractFlashcardViewer,
) {
    private val currentCard: Card
        get() = activity.currentCard!!

    private val getColUnsafe: Collection
        get() = activity.getColUnsafe

    /**
     Javascript Interface class for calling Java function from AnkiDroid WebView
     see js-api.js for available functions
     */

    private val context: Context = activity

    // Text to speech
    private val talker = JavaScriptTTS()

    // Speech to Text
    private val speechRecognizer = JavaScriptSTT(context)

    open fun convertToByteArray(
        apiContract: ApiContract,
        boolean: Boolean,
    ): ByteArray = ApiResult.Boolean(apiContract.isValid, boolean).toString().toByteArray()

    open fun convertToByteArray(
        apiContract: ApiContract,
        int: Int,
    ): ByteArray = ApiResult.Integer(apiContract.isValid, int).toString().toByteArray()

    open fun convertToByteArray(
        apiContract: ApiContract,
        long: Long,
    ): ByteArray = ApiResult.Long(apiContract.isValid, long).toString().toByteArray()

    open fun convertToByteArray(
        apiContract: ApiContract,
        string: String,
    ): ByteArray = ApiResult.String(apiContract.isValid, string).toString().toByteArray()

    /**
     * The method parse json data and return api contract object
     * @param byteArray
     * @return apiContract or null
     */
    private fun parseJsApiContract(byteArray: ByteArray): ApiContract? {
        try {
            val data = JSONObject(byteArray.decodeToString())
            val cardSuppliedApiVersion = data.optString("version", "")
            val cardSuppliedDeveloperContact = data.optString("developer", "")
            val cardSuppliedData = data.optString("data", "")
            val isValid = requireApiVersion(cardSuppliedApiVersion, cardSuppliedDeveloperContact)
            return ApiContract(isValid, cardSuppliedDeveloperContact, cardSuppliedData)
        } catch (j: JSONException) {
            Timber.w(j)
            activity.runOnUiThread {
                activity.showSnackbar(context.getString(R.string.invalid_json_data, j.localizedMessage))
            }
        }
        return null
    }

    /*
     * see 02-strings.xml
     * Show Error code when mark card or flag card unsupported
     * 1 - mark card
     * 2 - flag card
     *
     * show developer contact if js api used in card is deprecated
     */
    private fun showDeveloperContact(
        errorCode: Int,
        apiDevContact: String,
    ) {
        val errorMsg: String = context.getString(R.string.anki_js_error_code, errorCode)
        val snackbarMsg: String = context.getString(R.string.api_version_developer_contact, apiDevContact, errorMsg)

        activity.showSnackbar(snackbarMsg, Snackbar.LENGTH_INDEFINITE) {
            setMaxLines(3)
            setAction(R.string.reviewer_invalid_api_version_visit_documentation) {
                activity.openUrl("https://github.com/ankidroid/Anki-Android/wiki")
            }
        }
    }

    /**
     * Supplied api version must be equal to current api version to call mark card, toggle flag functions etc.
     */
    private fun requireApiVersion(
        apiVer: String,
        apiDevContact: String,
    ): Boolean {
        try {
            if (apiDevContact.isEmpty() || apiVer.isEmpty()) {
                activity.runOnUiThread {
                    activity.showSnackbar(context.getString(R.string.invalid_json_data, ""))
                }
                return false
            }
            val versionCurrent = Version.parse(AnkiDroidJsAPIConstants.CURRENT_JS_API_VERSION)
            val versionSupplied = Version.parse(apiVer)

            /*
             * if api major version equals to supplied major version then return true and also check for minor version and patch version
             * show toast for update and contact developer if need updates
             * otherwise return false
             */
            return when {
                versionSupplied == versionCurrent -> {
                    true
                }
                versionSupplied.isLowerThan(versionCurrent) -> {
                    activity.runOnUiThread {
                        activity.showSnackbar(context.getString(R.string.update_js_api_version, apiDevContact))
                    }
                    versionSupplied.isHigherThanOrEquivalentTo(Version.parse(AnkiDroidJsAPIConstants.MINIMUM_JS_API_VERSION))
                }
                else -> {
                    activity.runOnUiThread {
                        activity.showSnackbar(context.getString(R.string.valid_js_api_version, apiDevContact))
                    }
                    false
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "requireApiVersion::exception")
        }
        return false
    }

    /**
     * Handle js api request,
     * some of the methods are overridden in Reviewer.kt and default values are returned.
     * @param methodName
     * @param bytes
     * @param returnDefaultValues `true` if default values should be returned (if non-[Reviewer])
     * @return
     */
    @NeedsTest("setNoteTags: Test that tags are set for all edge cases")
    open suspend fun handleJsApiRequest(
        methodName: String,
        bytes: ByteArray,
        returnDefaultValues: Boolean = true,
    ) = withContext(Dispatchers.Main) {
        // the method will call to set the card supplied data and is valid version for each api request
        val apiContract = parseJsApiContract(bytes)!!
        // if api not init or is api not called from reviewer then return default -1
        // also other action will not be modified
        if (!apiContract.isValid or returnDefaultValues) {
            return@withContext convertToByteArray(apiContract, -1)
        }

        val cardDataForJsAPI = activity.getCardDataForJsApi()
        val apiParams = apiContract.cardSuppliedData

        return@withContext when (methodName) {
            "init" -> convertToByteArray(apiContract, true)
            "newCardCount" -> convertToByteArray(apiContract, cardDataForJsAPI.newCardCount)
            "lrnCardCount" -> convertToByteArray(apiContract, cardDataForJsAPI.lrnCardCount)
            "revCardCount" -> convertToByteArray(apiContract, cardDataForJsAPI.revCardCount)
            "eta" -> convertToByteArray(apiContract, cardDataForJsAPI.eta)
            "nextTime1" -> convertToByteArray(apiContract, cardDataForJsAPI.nextTime1)
            "nextTime2" -> convertToByteArray(apiContract, cardDataForJsAPI.nextTime2)
            "nextTime3" -> convertToByteArray(apiContract, cardDataForJsAPI.nextTime3)
            "nextTime4" -> convertToByteArray(apiContract, cardDataForJsAPI.nextTime4)
            "toggleFlag" -> {
                if (apiParams !in flagCommands) {
                    showDeveloperContact(ANKI_JS_ERROR_CODE_FLAG_CARD, apiContract.cardSuppliedDeveloperContact)
                    return@withContext convertToByteArray(apiContract, false)
                }
                convertToByteArray(apiContract, activity.executeCommand(flagCommands[apiParams]!!))
            }
            "markCard" ->
                processAction({
                    activity.executeCommand(ViewerCommand.MARK)
                }, apiContract, ANKI_JS_ERROR_CODE_MARK_CARD, ::convertToByteArray)
            "buryCard" -> processAction(activity::buryCard, apiContract, ANKI_JS_ERROR_CODE_BURY_CARD, ::convertToByteArray)
            "buryNote" -> processAction(activity::buryNote, apiContract, ANKI_JS_ERROR_CODE_BURT_NOTE, ::convertToByteArray)
            "suspendCard" -> processAction(activity::suspendCard, apiContract, ANKI_JS_ERROR_CODE_SUSPEND_CARD, ::convertToByteArray)
            "suspendNote" -> processAction(activity::suspendNote, apiContract, ANKI_JS_ERROR_CODE_SUSPEND_NOTE, ::convertToByteArray)
            "setCardDue" -> {
                try {
                    val days = apiParams.toInt()
                    if (days !in 0..9999) {
                        showDeveloperContact(ANKI_JS_ERROR_CODE_SET_DUE, apiContract.cardSuppliedDeveloperContact)
                        return@withContext convertToByteArray(apiContract, false)
                    }
                    activity.launchCatchingTask {
                        activity.rescheduleCards(listOf(currentCard.id), days)
                    }
                    return@withContext convertToByteArray(apiContract, true)
                } catch (_: NumberFormatException) {
                    showDeveloperContact(ANKI_JS_ERROR_CODE_SET_DUE, apiContract.cardSuppliedDeveloperContact)
                    return@withContext convertToByteArray(apiContract, false)
                }
            }
            "resetProgress" -> {
                val cardIds = listOf(currentCard.id)
                activity.launchCatchingTask { activity.resetCards(cardIds) }
                convertToByteArray(apiContract, true)
            }
            "cardMark" -> convertToByteArray(apiContract, currentCard.note(getColUnsafe).hasTag(getColUnsafe, "marked"))
            "cardFlag" -> convertToByteArray(apiContract, currentCard.userFlag())
            "cardReps" -> convertToByteArray(apiContract, currentCard.reps)
            "cardInterval" -> convertToByteArray(apiContract, currentCard.ivl)
            "cardFactor" -> convertToByteArray(apiContract, currentCard.factor)
            "cardMod" -> convertToByteArray(apiContract, currentCard.mod)
            "cardId" -> convertToByteArray(apiContract, currentCard.id)
            "cardNid" -> convertToByteArray(apiContract, currentCard.nid)
            "cardType" -> convertToByteArray(apiContract, currentCard.type.code)
            "cardDid" -> convertToByteArray(apiContract, currentCard.did)
            "cardLeft" -> convertToByteArray(apiContract, currentCard.left)
            "cardODid" -> convertToByteArray(apiContract, currentCard.oDid)
            "cardODue" -> convertToByteArray(apiContract, currentCard.oDue)
            "cardQueue" -> convertToByteArray(apiContract, currentCard.queue.code)
            "cardLapses" -> convertToByteArray(apiContract, currentCard.lapses)
            "cardDue" -> convertToByteArray(apiContract, currentCard.due)
            "deckName" -> convertToByteArray(apiContract, Decks.basename(activity.getColUnsafe.decks.name(currentCard.did)))
            "isActiveNetworkMetered" -> convertToByteArray(apiContract, NetworkUtils.isActiveNetworkMetered())
            "ttsSetLanguage" -> convertToByteArray(apiContract, talker.setLanguage(apiParams))
            "ttsSpeak" -> {
                val jsonObject = JSONObject(apiParams)
                val text = jsonObject.getString("text")
                val queueMode = jsonObject.getInt("queueMode")
                convertToByteArray(apiContract, talker.speak(text, queueMode))
            }
            "ttsIsSpeaking" -> convertToByteArray(apiContract, talker.isSpeaking)
            "ttsSetPitch" -> convertToByteArray(apiContract, talker.setPitch(apiParams.toFloat()))
            "ttsSetSpeechRate" -> convertToByteArray(apiContract, talker.setSpeechRate(apiParams.toFloat()))
            "ttsFieldModifierIsAvailable" -> {
                // Know if {{tts}} is supported - issue #10443
                // Return false for now
                convertToByteArray(apiContract, false)
            }
            "ttsStop" -> convertToByteArray(apiContract, talker.stop())
            "searchCard" -> {
                val intent =
                    Intent(context, CardBrowser::class.java).apply {
                        putExtra("currentCard", currentCard.id)
                        putExtra(CardBrowserViewModel.EXTRA_SEARCH_QUERY, apiParams)
                    }
                activity.startActivity(intent)
                convertToByteArray(apiContract, true)
            }
            "searchCardWithCallback" -> ankiSearchCardWithCallback(apiContract)
            "isDisplayingAnswer" -> convertToByteArray(apiContract, activity.isDisplayingAnswer)
            "addTagToCard" -> {
                activity.runOnUiThread { activity.showTagsDialog() }
                convertToByteArray(apiContract, true)
            }
            "isInFullscreen" -> convertToByteArray(apiContract, activity.isFullscreen)
            "isTopbarShown" -> convertToByteArray(apiContract, activity.prefShowTopbar)
            "isInNightMode" -> convertToByteArray(apiContract, activity.isInNightMode)
            "enableHorizontalScrollbar" -> {
                activity.webView!!.isHorizontalScrollBarEnabled = apiParams.toBoolean()
                convertToByteArray(apiContract, true)
            }
            "enableVerticalScrollbar" -> {
                activity.webView!!.isVerticalScrollBarEnabled = apiParams.toBoolean()
                convertToByteArray(apiContract, true)
            }
            "showNavigationDrawer" -> {
                activity.onNavigationPressed()
                convertToByteArray(apiContract, true)
            }
            "showOptionsMenu" -> {
                activity.openOptionsMenu()
                convertToByteArray(apiContract, true)
            }
            "showToast" -> {
                val jsonObject = JSONObject(apiParams)
                val text = jsonObject.getString("text")
                val shortLength = jsonObject.optBoolean("shortLength", true)
                val msgDecode = activity.decodeUrl(text)
                showThemedToast(context, msgDecode, shortLength)
                convertToByteArray(apiContract, true)
            }
            "showAnswer" -> {
                activity.displayCardAnswer()
                convertToByteArray(apiContract, true)
            }
            "answerEase1" -> {
                activity.flipOrAnswerCard(Rating.AGAIN)
                convertToByteArray(apiContract, true)
            }
            "answerEase2" -> {
                activity.flipOrAnswerCard(Rating.HARD)
                convertToByteArray(apiContract, true)
            }
            "answerEase3" -> {
                activity.flipOrAnswerCard(Rating.GOOD)
                convertToByteArray(apiContract, true)
            }
            "answerEase4" -> {
                activity.flipOrAnswerCard(Rating.EASY)
                convertToByteArray(apiContract, true)
            }

            "addTagToNote" -> {
                val jsonObject = JSONObject(apiParams)
                val noteId = jsonObject.getLong("noteId")
                val tag = jsonObject.getString("tag")
                val note =
                    getColUnsafe.getNote(noteId).apply {
                        addTag(tag)
                    }
                getColUnsafe.updateNote(note)
                convertToByteArray(apiContract, true)
            }

            "setNoteTags" -> {
                val jsonObject = JSONObject(apiParams)
                val noteId = currentCard.nid
                val tags = jsonObject.getJSONArray("tags")
                withCol {
                    fun Note.setTagsFromList(tagList: List<String>) {
                        val sanitizedTags = tagList.map { it.trim() }
                        val spaces = "\\s|\u3000".toRegex()
                        if (sanitizedTags.any { it.contains(spaces) }) {
                            throw IllegalArgumentException("Tags cannot contain spaces")
                        }
                        val tagsAsString = this@withCol.tags.join(sanitizedTags)
                        setTagsFromStr(this@withCol, tagsAsString)
                    }

                    val note =
                        getNote(noteId).apply {
                            setTagsFromList(tags.stringIterable().toList())
                        }
                    updateNote(note)
                }
                convertToByteArray(apiContract, true)
            }

            "getNoteTags" -> {
                val noteId = currentCard.nid
                val noteTags =
                    withCol {
                        getNote(noteId).tags
                    }
                convertToByteArray(apiContract, JSONArray(noteTags).toString())
            }

            "sttSetLanguage" -> convertToByteArray(apiContract, speechRecognizer.setLanguage(apiParams))
            "sttStart" -> {
                val callback =
                    object : JavaScriptSTT.SpeechRecognitionCallback {
                        override fun onResult(results: List<String>) {
                            activity.lifecycleScope.launch {
                                val apiResult = ApiResult.success(Json.encodeToString(ListSerializer(String.serializer()), results))
                                val jsonEncodedString = withContext(Dispatchers.Default) { JSONObject.quote(apiResult.toString()) }
                                activity.webView!!.evaluateJavascript("ankiSttResult($jsonEncodedString)", null)
                            }
                        }

                        override fun onError(errorMessage: String) {
                            activity.lifecycleScope.launch {
                                val apiResult = ApiResult.failure(errorMessage)
                                val jsonEncodedString = withContext(Dispatchers.Default) { JSONObject.quote(apiResult.toString()) }
                                activity.webView!!.evaluateJavascript("ankiSttResult($jsonEncodedString)", null)
                            }
                        }
                    }
                speechRecognizer.setRecognitionCallback(callback)
                convertToByteArray(apiContract, speechRecognizer.start())
            }
            "sttStop" -> convertToByteArray(apiContract, speechRecognizer.stop())
            else -> {
                showDeveloperContact(ANKI_JS_ERROR_CODE_ERROR, apiContract.cardSuppliedDeveloperContact)
                throw Exception("unhandled request: $methodName")
            }
        }
    }

    private fun processAction(
        action: () -> Boolean,
        apiContract: ApiContract,
        errorCode: Int,
        conversion: (ApiContract, Boolean) -> ByteArray,
    ): ByteArray {
        val status = action()
        if (!status) {
            showDeveloperContact(errorCode, apiContract.cardSuppliedDeveloperContact)
        }
        return conversion(apiContract, status)
    }

    @NeedsTest("needs coverage")
    private suspend fun ankiSearchCardWithCallback(apiContract: ApiContract): ByteArray =
        withContext(Dispatchers.Main) {
            val cards =
                try {
                    val searchString = withCol { SearchString.fromUserInput(apiContract.cardSuppliedData) }.getOrThrow()
                    searchForRows(searchString, SortOrder.UseCollectionOrdering, CardsOrNotes.CARDS)
                        .map { withCol { getCard(it.cardOrNoteId) } }
                } catch (_: Exception) {
                    activity.webView!!.evaluateJavascript(
                        "console.log('${context.getString(R.string.search_card_js_api_no_results)}')",
                        null,
                    )
                    showDeveloperContact(AnkiDroidJsAPIConstants.ANKI_JS_ERROR_CODE_SEARCH_CARD, apiContract.cardSuppliedDeveloperContact)
                    return@withContext convertToByteArray(apiContract, false)
                }
            val searchResult: MutableList<String> = ArrayList()
            for (card in cards) {
                val jsonObject = JSONObject()
                val fieldsData = card.note(getColUnsafe).fields
                val fieldsName = card.noteType(getColUnsafe).fieldsNames

                val noteId = card.nid
                val cardId = card.id
                jsonObject.put("cardId", cardId)
                jsonObject.put("noteId", noteId)

                val jsonFieldObject = JSONObject()
                fieldsName.zip(fieldsData).forEach { pair ->
                    jsonFieldObject.put(pair.component1(), pair.component2())
                }
                jsonObject.put("fieldsData", jsonFieldObject)

                searchResult.add(jsonObject.toString())
            }

            // quote result to prevent JSON injection attack
            val jsonEncodedString = JSONObject.quote(searchResult.toString())
            activity.runOnUiThread {
                activity.webView!!.evaluateJavascript("ankiSearchCard($jsonEncodedString)", null)
            }
            convertToByteArray(apiContract, true)
        }

    open class CardDataForJsApi {
        var newCardCount: Int = -1
        var lrnCardCount: Int = -1
        var revCardCount: Int = -1
        var eta = -1
        var nextTime1 = ""
        var nextTime2 = ""
        var nextTime3 = ""
        var nextTime4 = ""
    }

    sealed class ApiResult protected constructor(
        private val status: JvmBoolean,
    ) {
        class Boolean(
            status: JvmBoolean,
            val value: JvmBoolean,
        ) : ApiResult(status) {
            override fun putValue(o: JSONObject) {
                o.put(VALUE_KEY, value)
            }
        }

        class Integer(
            status: JvmBoolean,
            val value: JvmInt,
        ) : ApiResult(status) {
            override fun putValue(o: JSONObject) {
                o.put(VALUE_KEY, value)
            }
        }

        class Float(
            status: JvmBoolean,
            val value: JvmFloat,
        ) : ApiResult(status) {
            override fun putValue(o: JSONObject) {
                o.put(VALUE_KEY, value)
            }
        }

        class Long(
            status: JvmBoolean,
            val value: JvmLong,
        ) : ApiResult(status) {
            override fun putValue(o: JSONObject) {
                o.put(VALUE_KEY, value)
            }
        }

        class String(
            status: JvmBoolean,
            val value: JvmString,
        ) : ApiResult(status) {
            override fun putValue(o: JSONObject) {
                o.put(VALUE_KEY, value)
            }
        }

        abstract fun putValue(o: JSONObject)

        override fun toString() =
            JSONObject()
                .apply {
                    put(SUCCESS_KEY, status)
                    putValue(this)
                }.toString()

        @Suppress("RemoveRedundantQualifierName") // we don't want `String(true, value)`
        companion object {
            fun success(value: JvmString) = ApiResult.String(true, value)

            fun failure(value: JvmString) = ApiResult.String(false, value)
        }
    }

    class ApiContract(
        val isValid: Boolean,
        val cardSuppliedDeveloperContact: String,
        val cardSuppliedData: String,
    )

    companion object {
        /**
         * Key for a success value.
         */
        const val VALUE_KEY = "value"
        const val SUCCESS_KEY = "success"
    }
}
