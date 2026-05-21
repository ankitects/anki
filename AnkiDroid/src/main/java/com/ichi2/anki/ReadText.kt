/*
 * Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>
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
package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowManager.BadTokenException
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.cardviewer.SingleCardSide
import com.ichi2.anki.common.utils.android.HandlerUtils.postDelayedOnNewHandler
import com.ichi2.anki.i18n.getIso3LanguageOrNull
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.TTSTag
import com.ichi2.anki.provider.pureAnswer
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.openUrl
import com.ichi2.utils.message
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title
import timber.log.Timber
import java.lang.ref.WeakReference

object ReadText {
    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var textToSpeech: TextToSpeech? = null
        private set

    @get:VisibleForTesting(otherwise = VisibleForTesting.NONE)
    var textToSpeak: String? = null
        private set
    private lateinit var flashCardViewer: WeakReference<Context>
    private var did: DeckId = 0
    private var ord = 0
    var questionAnswer: CardSide? = null
        private set
    private const val NO_TTS = "0"
    private val ttsParams = Bundle()
    private var completionListener: ReadTextListener? = null

    private fun speak(
        text: String?,
        loc: String,
        queueMode: Int,
    ) {
        val result = textToSpeech!!.setLanguage(LanguageUtils.localeFromStringIgnoringScriptAndExtensions(loc))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            showThemedToast(
                flashCardViewer.get()!!,
                flashCardViewer.get()!!.getString(R.string.no_tts_available_message) +
                    " (" + loc + ")",
                false,
            )
            Timber.e("Error loading locale %s", loc)
        } else {
            if (textToSpeech!!.isSpeaking && queueMode == TextToSpeech.QUEUE_FLUSH) {
                Timber.d("tts engine appears to be busy... clearing queue")
                stopTts()
            }
            Timber.d("tts text '%s' to be played for locale (%s)", text, loc)
            textToSpeech!!.speak(textToSpeak, queueMode, ttsParams, "stringId")
        }
    }

    private fun getLanguage(
        did: DeckId,
        ord: Int,
        qa: CardSide,
    ): String = MetaDB.getLanguage(flashCardViewer.get()!!, did, ord, qa)

    /**
     * Ask the user what language they want.
     *
     * @param text The text to be read
     * @param did  The deck id
     * @param ord  The card template ordinal
     * @param qa   The card question or card answer
     */
    @SuppressLint("CheckResult")
    fun selectTts(
        text: String?,
        did: DeckId,
        ord: Int,
        qa: CardSide?,
    ) {
        // TODO: Consolidate with ReadText.readCardSide
        textToSpeak = text
        questionAnswer = qa
        ReadText.did = did
        ReadText.ord = ord
        val res = flashCardViewer.get()!!.resources
        val dialog = AlertDialog.Builder(flashCardViewer.get()!!)
        if (availableLocales().isEmpty()) {
            Timber.w("ReadText.textToSpeech() no TTS languages available")
            dialog
                .message(R.string.no_tts_available_message)
                .setIcon(R.drawable.ic_warning)
                .positiveButton(R.string.dialog_ok)
        } else {
            val localeMappings: List<Pair<String, CharSequence>> =
                mutableListOf<Pair<String, String>>().apply {
                    add(Pair(NO_TTS, res.getString(R.string.tts_no_tts))) // add option: "no tts"
                    val (validLocales, invalidLocales) =
                        availableLocales()
                            .sortedWith(compareBy { it.displayName })
                            .map { Pair(it.getIso3LanguageOrNull(), it.displayName) }
                            // getIso3LanguageOrNull returns null if invalid
                            // we could work around this, but ReadText is deprecated
                            .partition { it.first != null }

                    if (invalidLocales.isNotEmpty()) {
                        Timber.w("%d invalid languages", invalidLocales.size)
                    }
                    addAll(validLocales.map { Pair(it.first!!, it.second) })
                }
            Timber.i("showing 'select language' dialog")
            dialog
                .title(R.string.select_locale_title)
                .setItems(localeMappings.map { it.second }.toTypedArray()) { _, index ->
                    val locale = localeMappings[index].first
                    Timber.d("ReadText.selectTts() user chose locale '%s'", locale)
                    MetaDB.storeLanguage(
                        flashCardViewer.get()!!,
                        ReadText.did,
                        ReadText.ord,
                        questionAnswer!!,
                        locale,
                    )
                    if (locale != NO_TTS) {
                        speak(textToSpeak, locale, TextToSpeech.QUEUE_FLUSH)
                    } else {
                        completionListener!!.onDone(qa)
                    }
                }
        }
        // Show the dialog after short delay so that user gets a chance to preview the card
        showDialogAfterDelay(dialog, 500)
    }

    private fun showDialogAfterDelay(
        dialog: AlertDialog.Builder,
        delayMillis: Int,
    ) {
        postDelayedOnNewHandler({
            try {
                dialog.show()
            } catch (e: BadTokenException) {
                Timber.w(e, "Activity invalidated before TTS language dialog could display")
            }
        }, delayMillis.toLong())
    }

    /**
     * Read a card side using a TTS service.
     *
     * @param textsToRead      Data for the TTS to read
     * @param cardSide         Card side to be read; SoundSide.SOUNDS_QUESTION or SoundSide.SOUNDS_ANSWER.
     * @param did              Index of the deck containing the card.
     * @param ord              The card template ordinal.
     */
    fun readCardSide(
        textsToRead: List<TTSTag>,
        cardSide: CardSide,
        did: DeckId,
        ord: Int,
    ) {
        var isFirstText = true
        var playedSound = false
        for (textToRead in textsToRead) {
            if (textToRead.fieldText.isEmpty()) {
                continue
            }
            playedSound = playedSound or
                textToSpeech(
                    textToRead,
                    did,
                    ord,
                    cardSide,
                    if (isFirstText) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                )
            isFirstText = false
        }
        // if we didn't play a sound, call the completion listener
        if (!playedSound) {
            completionListener!!.onDone(cardSide)
        }
    }

    /**
     * Read the given text using an appropriate TTS voice.
     *
     *
     * The voice is chosen as follows:
     *
     *
     * 1. If localeCode is a non-empty string representing a locale in the format returned
     * by Locale.toString(), and a voice matching the language of this locale (and ideally,
     * but not necessarily, also the country and variant of the locale) is available, then this
     * voice is used.
     * 2. Otherwise, if the database contains a saved language for the given 'did', 'ord' and 'qa'
     * arguments, and a TTS voice matching that language is available, then this voice is used
     * (unless the saved language is NO_TTS, in which case the text is not read at all).
     * 3. Otherwise, the user is asked to select a language from among those for which a voice is
     * available.
     *
     * @param queueMode TextToSpeech.QUEUE_ADD or TextToSpeech.QUEUE_FLUSH.
     * @return false if a sound was not played
     */
    private fun textToSpeech(
        tag: TTSTag,
        did: DeckId,
        ord: Int,
        qa: CardSide,
        queueMode: Int,
    ): Boolean {
        textToSpeak = tag.fieldText
        questionAnswer = qa
        ReadText.did = did
        ReadText.ord = ord
        Timber.d("ReadText.textToSpeech() method started for string '%s', locale '%s'", tag.fieldText, tag.lang)
        var localeCode = tag.lang
        val originalLocaleCode = localeCode
        if (localeCode.isNotEmpty()) {
            if (!isLanguageAvailable(localeCode)) {
                localeCode = ""
            }
        }
        if (localeCode.isEmpty()) {
            // get the user's existing language preference
            localeCode = getLanguage(ReadText.did, ReadText.ord, questionAnswer!!)
            Timber.d("ReadText.textToSpeech() method found language choice '%s'", localeCode)
        }
        if (localeCode == NO_TTS) {
            // user has chosen not to read the text
            return false
        }
        if (localeCode.isNotEmpty() && isLanguageAvailable(localeCode)) {
            speak(textToSpeak, localeCode, queueMode)
            return true
        }

        // Otherwise ask the user what language they want to use
        if (originalLocaleCode.isNotEmpty()) {
            // (after notifying them first that no TTS voice was found for the locale
            // they originally requested)
            showThemedToast(
                flashCardViewer.get()!!,
                flashCardViewer.get()!!.getString(R.string.no_tts_available_message) +
                    " (" + originalLocaleCode + ")",
                false,
            )
        }
        selectTts(textToSpeak, ReadText.did, ReadText.ord, questionAnswer)
        return true
    }

    /**
     * Returns true if the TTS engine supports the language of the locale represented by localeCode
     * (which should be in the format returned by Locale.toString()), false otherwise.
     */
    private fun isLanguageAvailable(localeCode: String): Boolean =
        textToSpeech!!.isLanguageAvailable(LanguageUtils.localeFromStringIgnoringScriptAndExtensions(localeCode)) >=
            TextToSpeech.LANG_AVAILABLE

    fun initializeTts(
        context: Context,
        listener: ReadTextListener,
    ) {
        // Store weak reference to Activity to prevent memory leak
        flashCardViewer = WeakReference(context)
        completionListener = listener
        val ankiActivityContext = context as? AnkiActivity
        // Create new TTS object and setup its onInit Listener
        textToSpeech =
            TextToSpeech(context) { status: Int ->
                if (status == TextToSpeech.SUCCESS) {
                    if (availableLocales().isNotEmpty()) {
                        // notify the reviewer that TTS has been initialized
                        Timber.d("TTS initialized and available languages found")
                        (context as AbstractFlashcardViewer).ttsInitialized()
                    } else {
                        ankiActivityContext?.showSnackbar(R.string.no_tts_available_message)
                        Timber.w("TTS initialized but no available languages found")
                    }
                    textToSpeech!!.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onDone(arg0: String) {
                                listener.onDone(questionAnswer)
                            }

                            override fun onError(
                                utteranceId: String?,
                                errorCode: Int,
                            ) {
                                Timber.v(
                                    "Android TTS failed: %s (%d). Check logcat for error. " +
                                        "Indicates a problem with Android TTS engine.",
                                    errorToDeveloperString(errorCode),
                                    errorCode,
                                )
                                @StringRes val helpUrl = R.string.link_faq_tts
                                val ankiActivity = context as AnkiActivity
                                ankiActivity.mayOpenUrl(helpUrl)
                                // TODO: We can do better in this UI now we have a reason for failure
                                ankiActivity.showSnackbar(R.string.no_tts_available_message) {
                                    setAction(R.string.help) { openTtsHelpUrl(helpUrl) }
                                }
                            }

                            @Suppress("DeprecatedCallableAddReplaceWith")
                            @Deprecated("")
                            override fun onError(utteranceId: String) {
                                // required for UtteranceProgressListener, but also deprecated
                                Timber.e("onError(string) should not have been called")
                            }

                            override fun onStart(arg0: String) {
                                // no nothing
                            }
                        },
                    )
                } else {
                    showThemedToast(context, context.getString(R.string.no_tts_available_message), false)
                    Timber.w("TTS not successfully initialized")
                }
            }
    }

    fun errorToDeveloperString(errorCode: Int): String =
        when (errorCode) {
            TextToSpeech.ERROR -> "Generic failure"
            TextToSpeech.ERROR_SYNTHESIS -> "TTS engine failed to synthesize input"
            TextToSpeech.ERROR_INVALID_REQUEST -> "Invalid request"
            TextToSpeech.ERROR_NETWORK -> "Network connectivity problem"
            TextToSpeech.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            TextToSpeech.ERROR_NOT_INSTALLED_YET -> "Unfinished download of the voice data"
            TextToSpeech.ERROR_OUTPUT -> "Output error (audio device or a file)"
            TextToSpeech.ERROR_SERVICE -> "TTS service"
            else -> "Unhandled Error [$errorCode]"
        }

    fun openTtsHelpUrl(
        @StringRes helpUrl: Int,
    ) {
        flashCardViewer.get()!!.openUrl(helpUrl)
    }

    /**
     * Request that TextToSpeech is stopped and shutdown after it it no longer being used
     * by the context that initialized it.
     * No-op if the current instance of TextToSpeech was initialized by another Context
     * @param context The context used during [.initializeTts]
     */
    fun releaseTts(context: Context) {
        if (textToSpeech != null && flashCardViewer.get() === context) {
            textToSpeech!!.stop()
            textToSpeech!!.shutdown()
        }
    }

    fun stopTts() {
        if (textToSpeech != null) {
            textToSpeech!!.stop()
        }
    }

    fun closeForTests() {
        if (textToSpeech != null) {
            textToSpeech!!.shutdown()
        }
        textToSpeech = null
        MetaDB.close()
        System.gc()
    }

    @Suppress("DEPRECATION") // we'll be removing this functionality, little point in fixing
    private fun availableLocales() = TtsVoices.availableLocalesBlocking()

    interface ReadTextListener {
        fun onDone(playedSide: CardSide?)
    }
}

fun legacyGetTtsTags(
    col: Collection,
    card: Card,
    cardSide: SingleCardSide,
): List<TTSTag> {
    val cardSideContent: String =
        when (cardSide) {
            SingleCardSide.FRONT -> card.question(col)
            SingleCardSide.BACK -> card.pureAnswer(col)
        }
    return TtsParser.getTextsToRead(cardSideContent, TR.cardTemplatesBlank())
}
