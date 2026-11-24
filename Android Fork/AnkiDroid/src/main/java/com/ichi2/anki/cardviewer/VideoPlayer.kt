// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import com.ichi2.anki.common.utils.htmlEncode
import com.ichi2.anki.libanki.AvRef
import com.ichi2.anki.libanki.SoundOrVideoTag
import kotlinx.coroutines.CancellableContinuation
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Interacts with `<video>` tags, triggering the start and detecting completion of videos
 *
 * The tag should have the following attributes:
 * `data-file`: The HTMLEncoded filename of the video file
 * `data-play`: An [AvRef] in the form: `q:0`
 *
 * `data-file` was selected to select the file to play, as we do not have the [AvRef] to play here
 *
 * @see com.ichi2.anki.libanki.Sound.expandSounds
 */
class VideoPlayer(
    private val jsEval: JavascriptEvaluator,
) {
    private var continuation: CancellableContinuation<Unit>? = null

    fun playVideo(
        continuation: CancellableContinuation<Unit>,
        tag: SoundOrVideoTag,
    ) {
        this.continuation = continuation

        val fileNameToFind = tag.filename
        // find & play the video with a matching 'data-file' attribute

        // BUG: We don't have the index of the tag in the list
        // so the wrong video would be played if contained twice in the card content
        jsEval.evaluateAfterDOMContentLoaded(
            """
                    var videos = document.getElementsByTagName("video")
            
                    for (i = 0; i < videos.length; i++) {
                       var video = videos[i];
                       if (video.attributes['data-file'].value == "${fileNameToFind.htmlEncode()}") {
                           console.log("playing video: " + video.attributes['data-play'].value);
                           video.currentTime = 0;
                           video.play();
                           break;
                       }
                    }
                """,
        )
    }

    fun onVideoFinished() {
        Timber.v("video ended")
        continuation?.resume(Unit)
        continuation = null
    }

    fun onVideoPaused() {
        Timber.i("video paused")
        continuation?.resumeWithException(MediaException(MediaErrorBehavior.STOP_MEDIA))
        continuation = null
    }
}
