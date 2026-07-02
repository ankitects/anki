/*
 *  Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
 *  Copyright (c) 2014 Timothy rae <perceptualchaos2@gmail.com>
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.multimedia

import com.ichi2.anki.CollectionManager
import com.ichi2.anki.common.utils.htmlEncode
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.libanki.AvRef
import com.ichi2.anki.libanki.AvTag
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Sound
import com.ichi2.anki.libanki.Sound.AV_PLAYLINK_RE
import com.ichi2.anki.libanki.Sound.replaceAvRefsWith
import com.ichi2.anki.libanki.SoundOrVideoTag
import com.ichi2.anki.libanki.SoundOrVideoTag.Type
import com.ichi2.anki.libanki.TTSTag
import com.ichi2.anki.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.anki.libanki.getFileUri
import com.ichi2.anki.utils.CollectionPreferences
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.nio.file.Paths

/**
 * Takes content with [AvRef]s and expands them to reference the media file
 *
 * * Videos are replaced with `<video>`
 * * Audio is replaced with <a href="playsound:">
 *
 * @param content card content to be rendered that may contain embedded audio
 *
 * @return content with [AvRef]s replaced with HTML to play the file
 */
@Suppress("HtmlUnknownAttribute", "HtmlDeprecatedAttribute")
fun expandSounds(
    content: String,
    renderOutput: TemplateRenderOutput,
    showAudioPlayButtons: Boolean,
    mediaDir: File,
) = replaceAvRefsWith(content, renderOutput) { tag, playTag ->

    fun AvRef.asHtmlAudio(): String {
        if (!showAudioPlayButtons) return ""
        val playsound = "playsound:${this.side}:${this.index}"

        @Language("HTML")
        val result = """<a class="replay-button soundLink" href=$playsound><span>
                        <svg class="playImage" viewBox="0 0 64 64" version="1.1">
                            <circle cx="32" cy="32" r="29" fill="lightgrey"/>
                            <path d="M56.502,32.301l-37.502,20.101l0.329,-40.804l37.173,20.703Z" fill="black"/>Replay
                        </svg>
                    </span></a>"""
        return result
    }

    fun SoundOrVideoTag.asHtmlVideo(): String {
        val filename = this.filename
        val path = Paths.get(mediaDir.absolutePath, filename).toString()
        val uri = getFileUri(path)

        val playsound = "${playTag.side}:${playTag.index}"

        val onEnded = """window.location.href = "videoended:$playsound";"""
        val onPause = """if (this.currentTime != this.duration) { window.location.href = "videopause:$playsound"; }"""

        // TODO: Make the loading screen nicer if the video doesn't autoplay
        @Language("HTML")
        val result =
            """<video
                    | src="$uri"
                    | controls
                    | data-file="${filename.htmlEncode()}"
                    | onended='$onEnded'
                    | onpause='$onPause'
                    | data-play="$playsound" controlsList="nodownload"></video>
            """.trimMargin()
        return result
    }

    when (tag) {
        is TTSTag -> playTag.asHtmlAudio()
        is SoundOrVideoTag -> {
            when (tag.getTagType(mediaDir)) {
                Type.AUDIO -> playTag.asHtmlAudio()
                Type.VIDEO -> tag.asHtmlVideo()
            }
        }
    }
}

/** Extract av tag from playsound:q:x link */
suspend fun getAvTag(
    card: Card,
    url: String,
): AvTag? =
    AV_PLAYLINK_RE.matchEntire(url)?.let {
        val values = it.groupValues
        val questionSide = values[1] == "q"
        val index = values[2].toInt()
        val tags =
            CollectionManager.withCol {
                if (questionSide) {
                    card.questionAvTags(this)
                } else {
                    card.answerAvTags(this)
                }
            }
        if (index < tags.size) {
            tags[index]
        } else {
            null
        }
    }

/**
 * Return card text with play buttons added, or stripped.
 *
 * @param text A string, maybe containing `[anki:play]` tags to replace
 * @param renderOutput Context: whether a file is audio or video
 *
 * @see AvRef
 */
suspend fun replaceAvRefsWithPlayButtons(
    text: String,
    renderOutput: TemplateRenderOutput,
): String {
    val mediaDir = CollectionManager.withCol { media.dir }
    val hidePlayButtons = CollectionPreferences.getHidePlayAudioButtons()
    return expandSounds(text, renderOutput, showAudioPlayButtons = !hidePlayButtons, mediaDir)
}

fun SoundOrVideoTag.getTagType(mediaDir: File): Type {
    val extension = filename.substringAfterLast(".", "")
    return when (extension) {
        in Sound.VIDEO_ONLY_EXTENSIONS -> Type.VIDEO
        in Sound.AUDIO_OR_VIDEO_EXTENSIONS -> {
            val file = File(mediaDir, filename)
            if (isAudioFileInVideoContainer(file) == true) {
                Type.AUDIO
            } else {
                Type.VIDEO
            }
        }
        // assume audio if we don't know. Our audio code is more resilient than HTML video
        else -> Type.AUDIO
    }
}

/**
 * Whether a video file only contains an audio stream
 *
 * @return `null` - file is not a video, or not found
 */
@VisibleForTesting
fun isAudioFileInVideoContainer(file: File): Boolean? {
    if (file.extension !in Sound.VIDEO_ONLY_EXTENSIONS && file.extension !in Sound.AUDIO_OR_VIDEO_EXTENSIONS) {
        return null
    }

    if (file.extension in Sound.VIDEO_ONLY_EXTENSIONS) return false

    // file.extension is in AUDIO_OR_VIDEO_EXTENSIONS
    if (!file.exists()) return null

    // Also check that there is a video thumbnail, as some formats like mp4 can be audio only
    val isVideo = CompatHelper.compat.hasVideoThumbnail(file.absolutePath) ?: return null
    return !isVideo
}
