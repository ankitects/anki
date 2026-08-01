/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import androidx.annotation.VisibleForTesting
import androidx.core.net.toFile
import com.ichi2.anki.libanki.TtsPlayer
import com.ichi2.anki.pages.AnkiServer.Companion.LOCALHOST
import timber.log.Timber
import java.io.File

/** Handles logic for displaying help for missing media files  */
class MediaErrorHandler : MediaErrorListener {
    companion object {
        /** Specify a maximum number of times to display, as it's somewhat annoying  */
        const val MAX_DISPLAY_TIMES = 2
    }

    constructor()
    constructor(onMediaError: ((String) -> Unit), onTtsError: ((TtsPlayer.TtsError) -> Unit)) {
        this.onMediaError = onMediaError
        this.onTtsError = onTtsError
    }

    // TODO turn into `val` once the legacy study screen is removed
    @VisibleForTesting
    var onMediaError: ((String) -> Unit)? = null

    @VisibleForTesting
    var onTtsError: ((TtsPlayer.TtsError) -> Unit)? = null

    private var missingMediaCount = 0
    private var hasExecuted = false

    private var automaticTtsFailureCount = 0

    override fun onError(uri: Uri): MediaErrorBehavior {
        if (uri.scheme != "file") {
            return MediaErrorBehavior.CONTINUE_MEDIA
        }

        val file = uri.toFile()
        // There is a multitude of transient issues with the MediaPlayer.
        // Retrying fixes most of these
        if (file.exists()) return MediaErrorBehavior.RETRY_MEDIA

        onMediaError?.let { callback ->
            processMissingMedia(file, callback)
        }
        return MediaErrorBehavior.CONTINUE_MEDIA
    }

    override fun onMediaPlayerError(
        mp: MediaPlayer?,
        which: Int,
        extra: Int,
        uri: Uri,
    ): MediaErrorBehavior {
        Timber.w("Media Error: (%d, %d)", which, extra)
        return onError(uri)
    }

    override fun onTtsError(
        error: TtsPlayer.TtsError,
        isAutomaticPlayback: Boolean,
    ) {
        onTtsError?.let { callback ->
            processTtsFailure(error, isAutomaticPlayback, callback)
        }
    }

    fun processFailure(
        request: WebResourceRequest,
        onFailure: (String) -> Unit,
    ) {
        // We do not want this to trigger more than once on the same side of the card as the UI will flicker.
        if (hasExecuted) return

        // The UX of the snackbar is annoying, as it obscures the content. Assume that if a user ignores it twice, they don't care.
        if (missingMediaCount >= MAX_DISPLAY_TIMES) return

        val url = request.url
        // We could do better here (external images failing due to no HTTPS), but failures can occur due to no network.
        // As we don't yet check the error data, we don't know.
        // Therefore limit this feature to the common case of local files, which should always work.
        if (url.host != LOCALHOST) return

        try {
            val filename = URLUtil.guessFileName(url.toString(), null, null)
            onFailure.invoke(filename)
            missingMediaCount++
        } catch (e: Exception) {
            Timber.w(e, "Failed to notify UI of media failure")
        } finally {
            hasExecuted = true
        }
    }

    fun processMissingMedia(
        file: File,
        onFailure: (String) -> Unit,
    ) {
        // We want this to trigger more than once on the same side - as the user is in control of pressing "play"
        // and we want to provide feedback
        // The UX of the snackbar is annoying, as it obscures the content. Assume that if a user ignores it twice, they don't care.
        if (missingMediaCount >= MAX_DISPLAY_TIMES) return

        try {
            val fileName = file.name
            onFailure.invoke(fileName)
            if (!hasExecuted) {
                missingMediaCount++
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to notify UI of media failure")
        } finally {
            hasExecuted = true
        }
    }

    fun onCardSideChange() {
        hasExecuted = false
    }

    fun processTtsFailure(
        error: TtsPlayer.TtsError,
        playingAutomatically: Boolean,
        errorHandler: (TtsPlayer.TtsError) -> Unit,
    ) {
        // if the user is playing a single sound explicitly, we want to provide feedback
        if (playingAutomatically && automaticTtsFailureCount++ >= 3) {
            Timber.v("Ignoring TTS Error: %s. failure limit exceeded", error)
            return
        }

        Timber.w("displaying error for %s", error)
        // Maybe specifically check for APP_TTS_INIT_TIMEOUT

        errorHandler.invoke(error)
    }
}
