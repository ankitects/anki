/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
 */

package com.ichi2.anki.cardviewer

import android.media.MediaPlayer
import android.net.Uri
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.AbstractFlashcardViewer.Companion.getMediaBaseUrl
import com.ichi2.anki.AndroidTtsError
import com.ichi2.anki.AndroidTtsPlayer
import com.ichi2.anki.CollectionHelper.getMediaDirectory
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.cardviewer.MediaErrorBehavior.CONTINUE_MEDIA
import com.ichi2.anki.cardviewer.MediaErrorBehavior.RETRY_MEDIA
import com.ichi2.anki.cardviewer.MediaErrorBehavior.STOP_MEDIA
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.libanki.AvTag
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.SoundOrVideoTag
import com.ichi2.anki.libanki.TTSTag
import com.ichi2.anki.libanki.TtsPlayer
import com.ichi2.anki.reviewer.CardSide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.Closeable

/**
 * Handles the two ways an Anki card defines sound:
 * * Regular Sound (file-based, mp3 etc..): [SoundOrVideoTag]
 *   *  No docs for [sound:], but this handles Sound or Video with a reference to the file
 *   * `[sound:audio.mp3]` in a field
 *   * `[sound:video.mp4]` in a field
 *  * in the media directory.
 * * Text to Speech [TTSTag]
 *   * [docs][https://docs.ankiweb.net/templates/fields.html?highlight=tts#text-to-speech]
 *   * `{{tts en_GB:Front}}` on the card template
 *
 * This class combines the above concerns behind an "adapter" interface in order to simplify complexity.
 *
 * **Public interface**
 * * [playAllForSide]
 * * [replayAll]
 * * [playOne]
 * * [stop]
 * * [loadCardAvTags] - informs the class of whether we're on the front/back of a card
 *
 * @see AvTag
 *
 * [setOnMediaGroupCompletedListener] can be used to call
 * something when [playAllForSide] or [replayAll] completes
 */
@NeedsTest("Integration test: A video is autoplayed if it's the first media on a card")
@NeedsTest("A sound is played after a video finishes")
@NeedsTest("Pausing a video calls onMediaGroupCompleted")
class CardMediaPlayer : Closeable {
    private val soundTagPlayer: SoundTagPlayer
    private val ttsPlayer: Deferred<TtsPlayer>
    private val mediaErrorListener: MediaErrorListener

    @VisibleForTesting
    constructor(soundTagPlayer: SoundTagPlayer, ttsPlayer: Deferred<TtsPlayer>, mediaErrorListener: MediaErrorListener) {
        this.soundTagPlayer = soundTagPlayer
        this.ttsPlayer = ttsPlayer
        this.mediaErrorListener = mediaErrorListener
    }

    constructor(javascriptEvaluator: JavascriptEvaluator, mediaErrorListener: MediaErrorListener) {
        this.mediaErrorListener = mediaErrorListener
        this.soundTagPlayer =
            SoundTagPlayer(
                soundUriBase = getMediaBaseUrl(getMediaDirectory(appContext)),
                videoPlayer = VideoPlayer(javascriptEvaluator),
            )
        this.ttsPlayer = scope.async { AndroidTtsPlayer.createInstance(scope) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Serializes playbacks to avoid overloading the thread pool and a potential deadlock */
    private val playbackMutex = Mutex()

    private var questionAvTags: List<AvTag> = emptyList()
    private var answerAvTags: List<AvTag> = emptyList()

    var config: CardSoundConfig? = null
        private set

    var isEnabled: Boolean = true
        private set

    suspend fun setEnabled(enabled: Boolean) {
        if (!enabled) stop()
        this.isEnabled = enabled
    }

    @VisibleForTesting
    var playAvTagsJob: Job? = null
    val isPlaying get() = playAvTagsJob != null

    private var onMediaGroupCompleted: (suspend () -> Unit)? = null

    fun setOnMediaGroupCompletedListener(listener: (suspend () -> Unit)?) {
        onMediaGroupCompleted = listener
    }

    suspend fun loadCardAvTags(card: Card) {
        Timber.i("loading av tags for card %d", card.id)
        stop()
        val renderOutput = withCol { card.renderOutput(this) }
        val autoPlay = withCol { card.autoplay(this) }
        this.questionAvTags = renderOutput.questionAvTags
        this.answerAvTags = renderOutput.answerAvTags

        val currentConfig = config
        val needsConfigUpdate =
            currentConfig == null ||
                !currentConfig.appliesTo(card) ||
                autoPlay != currentConfig.autoplay

        if (needsConfigUpdate) {
            config = withCol { CardSoundConfig.create(this@withCol, card) }
        }
    }

    /**
     * Ensures that [questionAvTags] and [answerAvTags] are loaded
     *
     * Does not affect playback if they are
     */
    suspend fun ensureAvTagsLoaded(card: Card) {
        if (config?.appliesTo(card) == true) return

        Timber.i("loading sounds for card %d", card.id)
        val renderOutput = withCol { card.renderOutput(this) }
        this.questionAvTags = renderOutput.questionAvTags
        this.answerAvTags = renderOutput.answerAvTags

        val currentConfig = config
        if (currentConfig == null || !currentConfig.appliesTo(card)) {
            config = withCol { CardSoundConfig.create(this@withCol, card) }
        }
    }

    suspend fun autoplayAllForSide(cardSide: CardSide) {
        if (config?.autoplay == true) {
            playAllForSide(cardSide)
        }
    }

    suspend fun playAllForSide(cardSide: CardSide) {
        if (!isEnabled) return
        playAvTagsJob =
            playbackMutex.withLock {
                playAvTagsJob?.cancelAndJoin()
                scope.launch {
                    Timber.i("playing sounds for %s", cardSide)
                    playAllAvTagsInternal(cardSide, isAutomaticPlayback = true)
                    playAvTagsJob = null
                }
            }
    }

    suspend fun playOne(tag: AvTag) {
        if (!isEnabled) return

        suspend fun play(tag: AvTag) = play(tag, isAutomaticPlayback = false)

        suspend fun retry() {
            try {
                play(tag)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "failed to replay media")
            }
        }

        playAvTagsJob =
            playbackMutex.withLock {
                playAvTagsJob?.cancelAndJoin()
                Timber.i("playing one AV Tag")

                scope.launch {
                    try {
                        play(tag)
                    } catch (e: MediaException) {
                        when (e.continuationBehavior) {
                            RETRY_MEDIA -> retry()
                            CONTINUE_MEDIA, STOP_MEDIA -> { }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Exception playing AV Tag")
                    }
                    Timber.v("completed playing one AV Tag")
                    playAvTagsJob = null
                }
            }
    }

    suspend fun stop() {
        if (isPlaying) Timber.i("stopping playing all AV tags")
        playAvTagsJob?.cancelAndJoin()
    }

    override fun close() {
        soundTagPlayer.release()
        ttsPlayer.close(logPrefix = "ttsPlayer")
        scope.cancel()
    }

    /**
     * Obtains all the [AvTag]s for the [cardSide] and plays them sequentially
     */
    private suspend fun playAllAvTagsInternal(
        cardSide: CardSide,
        isAutomaticPlayback: Boolean,
    ) {
        if (!isEnabled) return
        val avTagList =
            when (cardSide) {
                CardSide.QUESTION -> questionAvTags
                CardSide.ANSWER -> answerAvTags
                CardSide.BOTH -> questionAvTags + answerAvTags
            }

        try {
            for ((index, avTag) in avTagList.withIndex()) {
                Timber.d("playing AV Tag %d/%d", index + 1, avTagList.size)
                if (!play(avTag, isAutomaticPlayback)) {
                    Timber.d("stopping AV Tag playback early")
                    return
                }
            }
        } finally {
            // call the completion listener, even if a CancellationException was thrown
            onMediaGroupCompleted?.invoke()
        }
    }

    /**
     * Plays the provided [tag] and returns whether playback should continue
     * @return whether playback should continue: `true`: continue, `false`: stop playback
     */
    private suspend fun play(
        tag: AvTag,
        isAutomaticPlayback: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            suspend fun play() {
                ensureActive()
                when (tag) {
                    is SoundOrVideoTag -> soundTagPlayer.play(tag, mediaErrorListener)
                    is TTSTag -> {
                        awaitTtsPlayer(isAutomaticPlayback)?.play(tag)?.error?.let {
                            mediaErrorListener.onTtsError(it, isAutomaticPlayback)
                        }
                    }
                }
                ensureActive()
            }

            try {
                play()
            } catch (e: MediaException) {
                when (e.continuationBehavior) {
                    STOP_MEDIA -> return@withContext false
                    CONTINUE_MEDIA -> return@withContext true
                    RETRY_MEDIA -> {
                        try {
                            Timber.i("retrying media")
                            play()
                            Timber.i("retry succeeded")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.w(e, "retry media failed")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Unexpected media exception. Continuing")
            }
            return@withContext true
        }

    /** Whether the provided side has available media */
    fun hasMedia(displayAnswer: Boolean): Boolean = if (displayAnswer) answerAvTags.isNotEmpty() else questionAvTags.isNotEmpty()

    /**
     * Replays all sounds for [side], calling [onMediaGroupCompleted] when completed
     */
    suspend fun replayAll(side: SingleCardSide) =
        when (side) {
            SingleCardSide.BACK -> if (config?.replayQuestion == true) playAllForSide(CardSide.BOTH) else playAllForSide(CardSide.ANSWER)
            SingleCardSide.FRONT -> playAllForSide(CardSide.QUESTION)
        }

    private suspend fun awaitTtsPlayer(isAutomaticPlayback: Boolean): TtsPlayer? {
        val player =
            withTimeoutOrNull(TTS_PLAYER_TIMEOUT_MS) {
                ttsPlayer.await()
            }
        if (player == null) {
            Timber.v("timeout waiting for TTS Player")
            val error = AndroidTtsError.InitTimeout
            mediaErrorListener.onTtsError(error, isAutomaticPlayback)
        }
        return player
    }

    @NeedsTest("finish moves to next sound")
    fun onVideoFinished() {
        soundTagPlayer.videoPlayer.onVideoFinished()
    }

    @NeedsTest("pause starts automatic answer")
    fun onVideoPaused() {
        Timber.i("video paused")
        soundTagPlayer.videoPlayer.onVideoPaused()
    }

    companion object {
        private const val TTS_PLAYER_TIMEOUT_MS = 2_500L
    }
}

/**
 * Cancels the [Deferred] and safely closes its resulting [Closeable] upon completion.
 *
 * The deferred is cancelled immediately.
 * When it completes, the underlying [Closeable] is closed.
 *
 * @param logPrefix Prefix used when logging
 */
private fun Deferred<Closeable>.close(logPrefix: String) {
    this.cancel()
    this.invokeOnCompletion {
        try {
            this.getCompleted().close()
            Timber.d("$logPrefix closed")
        } catch (_: CancellationException) {
            // Ignore: no value was produced, nothing to close
        } catch (e: Exception) {
            Timber.w(e, "$logPrefix close()")
        }
    }
}

interface MediaErrorListener {
    @CheckResult
    fun onError(uri: Uri): MediaErrorBehavior

    @CheckResult
    fun onMediaPlayerError(
        mp: MediaPlayer?,
        which: Int,
        extra: Int,
        uri: Uri,
    ): MediaErrorBehavior

    fun onTtsError(
        error: TtsPlayer.TtsError,
        isAutomaticPlayback: Boolean,
    )
}

enum class MediaErrorBehavior {
    /** Stop playing media */
    STOP_MEDIA,

    /** Continue to the next media (if any) */
    CONTINUE_MEDIA,

    /** Retry the current media */
    RETRY_MEDIA,
}

/** An exception thrown when playing a sound, and how to continue playing sounds */
class MediaException : Exception {
    val continuationBehavior: MediaErrorBehavior
    constructor(errorHandling: MediaErrorBehavior) : super() {
        this.continuationBehavior = errorHandling
    }
    constructor(errorHandling: MediaErrorBehavior, exception: Exception) : super(exception) {
        this.continuationBehavior = errorHandling
    }
}
