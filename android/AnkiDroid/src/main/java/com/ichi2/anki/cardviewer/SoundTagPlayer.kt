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
 */

package com.ichi2.anki.cardviewer

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.annotation.CheckResult
import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC
import androidx.media3.common.audio.AudioFocusRequestCompat
import androidx.media3.common.audio.AudioManagerCompat
import androidx.media3.common.util.UnstableApi
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.ensureActive
import com.ichi2.anki.libanki.SoundOrVideoTag
import com.ichi2.anki.multimedia.getTagType
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Player for (`[sound:...]`): [SoundOrVideoTag]  */
@OptIn(UnstableApi::class)
@NeedsTest("CardSoundConfig.autoplay should mean that video also isn't played automatically")
class SoundTagPlayer(
    private val soundUriBase: String,
    val videoPlayer: VideoPlayer,
) {
    private var mediaPlayer: MediaPlayer? = null

    private val music =
        AudioAttributes
            .Builder()
            .setContentType(AUDIO_CONTENT_TYPE_MUSIC)
            .build()

    /**
     * AudioManager to request/release audio focus
     */
    private var audioManager: AudioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // the same instance of an AudioFocusRequestCompat must be used to cancel focus
    private val audioFocusRequest: AudioFocusRequestCompat by lazy {
        AudioFocusRequestCompat
            .Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setOnAudioFocusChangeListener { }
            .build()
    }

    /**
     * @throws MediaException if the file does not exist, or if media playing fails
     * @param mediaErrorListener handles a sound error and returns how to continue playing sounds
     */
    suspend fun play(
        tag: SoundOrVideoTag,
        mediaErrorListener: MediaErrorListener?,
    ) {
        val tagType = tag.getType()
        suspendCancellableCoroutine { continuation ->
            Timber.d("Playing SoundOrVideoTag")
            when (tagType) {
                SoundOrVideoTag.Type.AUDIO -> playSound(continuation, tag, mediaErrorListener)
                SoundOrVideoTag.Type.VIDEO -> playVideo(continuation, tag)
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    fun playVideo(
        continuation: CancellableContinuation<Unit>,
        tag: SoundOrVideoTag,
    ) {
        Timber.d("Playing video")
        videoPlayer.playVideo(continuation, tag)
    }

    private fun playSound(
        continuation: CancellableContinuation<Unit>,
        tag: SoundOrVideoTag,
        mediaErrorListener: MediaErrorListener?,
    ) {
        requireNewMediaPlayer().apply {
            continuation.invokeOnCancellation {
                Timber.i("stopping MediaPlayer due to cancellation")
                this@SoundTagPlayer.stop()
            }
            setOnCompletionListener {
                Timber.v("finished playing SoundOrVideoTag successfully")
                abandonAudioFocus()
                // guard against a potential issue: task cancellation
                if (!continuation.isCompleted) {
                    continuation.resume(Unit)
                }
            }
            val tagUri = tag.filename.toUri()
            val soundUri =
                if (tagUri.scheme != null) {
                    tagUri
                } else {
                    (soundUriBase + Uri.encode(tag.filename)).toUri()
                }
            setAudioAttributes(music.platformAudioAttributes)
            setOnErrorListener { mp, what, extra ->
                Timber.w("Media error %d", what)
                abandonAudioFocus()
                val continuationBehavior =
                    mediaErrorListener?.onMediaPlayerError(mp, what, extra, soundUri) ?: MediaErrorBehavior.CONTINUE_MEDIA
                // 15103: setOnErrorListener can be invoked after task cancellation
                if (!continuation.isCompleted) {
                    continuation.resumeWithException(MediaException(continuationBehavior))
                }
                true // do not call onCompletionListen
            }

            try {
                awaitSetDataSource(soundUri.toString())
            } catch (e: Exception) {
                continuation.ensureActive()
                val continuationBehavior = mediaErrorListener?.onError(soundUri) ?: MediaErrorBehavior.CONTINUE_MEDIA
                val exception = MediaException(continuationBehavior, e)
                return continuation.resumeWithException(exception)
            }

            if (requestAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                continuation.ensureActive()
                Timber.d("starting sound tag")
                start()
            } else {
                Timber.d("unable to get audio focus, cancelling work")
                continuation.cancel()
            }
        }
    }

    /**
     * Releases the media players.
     */
    fun release() {
        Timber.d("Releasing sounds and abandoning audio focus")
        mediaPlayer?.let {
            // Required to remove warning: "mediaplayer went away with unhandled events"
            // https://stackoverflow.com/questions/9609479/android-mediaplayer-went-away-with-unhandled-events
            it.reset()
            it.release()
            mediaPlayer = null
        }
        abandonAudioFocus()
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            Timber.w(e, "stopSounds()")
        }
        abandonAudioFocus()
    }

    /**
     * Produces a usable [MediaPlayer], either creating a new instance or resetting the current
     * instance
     */
    private fun requireNewMediaPlayer(): MediaPlayer {
        if (mediaPlayer == null) {
            Timber.d("Creating media player for playback")
            // PERF: see if this is slow, maybe move to a task on instantiation
            mediaPlayer = MediaPlayer()
        } else {
            Timber.d("Resetting media for playback")
            mediaPlayer!!.reset()
        }
        return mediaPlayer!!
    }

    /**
     * @throws IllegalStateException if the sound player is in an invalid state
     * @throws java.io.FileNotFoundException file is not found
     * @throws java.io.IOException: Prepare failed.: status=0x1
     */
    private fun MediaPlayer.awaitSetDataSource(uri: String) {
        setDataSource(uri)
        prepare()
    }

    @CheckResult
    private fun requestAudioFocus(): Int {
        Timber.d("Requesting audio focus")
        return AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)
    }

    private fun abandonAudioFocus(): Int {
        Timber.d("Abandoning audio focus")
        return AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
    }
}

suspend fun SoundOrVideoTag.getType(): SoundOrVideoTag.Type = getTagType(withCol { media.dir })
