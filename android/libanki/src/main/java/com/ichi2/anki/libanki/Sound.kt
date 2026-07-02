/*
 *  Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
 *  Copyright (c) 2014 Timothy rae <perceptualchaos2@gmail.com>
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
 *
 *  This file incorporates code under the following license
 *  https://github.com/ankitects/anki/blob/2.1.34/pylib/anki/sound.py
 *  https://github.com/ankitects/anki/blob/3378e476e6c63f46f6cbaab98ac679c7eb8dc5a0/pylib/anki/sound.py#L4
 *
 *    Copyright: Ankitects Pty Ltd and contributors
 *    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.TemplateManager.TemplateRenderContext.TemplateRenderOutput
import com.ichi2.anki.libanki.utils.NotInPyLib
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.net.URI
import java.util.regex.Pattern

/**
 * Records information about a text to speech tag.
 *
 * @param speed speed of speech, where `1.0f` is normal speed. `null`: use system
 */
data class TTSTag(
    val fieldText: String,
    /**
     * Language may be empty if coming from AnkiDroid reading the whole card
     */
    val lang: String,
    val voices: List<String>,
    val speed: Float?,
    /** each arg should be in the form 'foo=bar' */
    val otherArgs: List<String>,
) : AvTag()

/**
 * Contains the filename inside a `[sound:...]` tag.
 */
data class SoundOrVideoTag(
    val filename: String,
) : AvTag() {
    enum class Type {
        AUDIO,
        VIDEO,
    }
}

/**
 * [Regex] used to identify the markers for sound files
 */
val SOUND_RE = Pattern.compile("\\[sound:([^\\[\\]]*)]").toRegex()

/** In python, this is a union of [TTSTag] and [SoundOrVideoTag] */
sealed class AvTag

fun stripAvRefs(
    text: String,
    replacement: String = "",
) = AvRef.REGEX.replace(text, replacement)

// not in libAnki
object Sound {
    val VIDEO_ONLY_EXTENSIONS = setOf("mov", "mkv")

    /** Extensions that can be audio-only using a video wrapper */
    val AUDIO_OR_VIDEO_EXTENSIONS = setOf("mp4", "mpg", "mpeg", "webm")

    // Methods
    val AV_PLAYLINK_RE = Regex("playsound:(.):(\\d+)")

    /**
     * Replaces `[anki:play:q:0]` with `[sound:...]`
     */
    fun replaceWithSoundTags(
        content: String,
        renderOutput: TemplateRenderOutput,
    ): String =
        replaceAvRefsWith(content, renderOutput) { tag, _ ->
            if (tag !is SoundOrVideoTag) null else "[sound:${tag.filename}]"
        }

    /**
     * Replaces [AvRef]s using the provided [processTag] function
     *
     * @param renderOutput context
     * @param processTag the text to replace the [AvTag] with, or `null` to perform no replacement
     */
    @Language("HTML")
    fun replaceAvRefsWith(
        content: String,
        renderOutput: TemplateRenderOutput,
        processTag: (AvTag, AvRef) -> String?,
    ): String {
        return AvRef.REGEX.replace(content) { match ->
            val avRef = AvRef.from(match) ?: return@replace match.value

            val tag =
                when (avRef.side) {
                    "q" -> renderOutput.questionAvTags.getOrNull(avRef.index)
                    "a" -> renderOutput.answerAvTags.getOrNull(avRef.index)
                    else -> null
                } ?: return@replace match.value

            return@replace processTag(tag, avRef) ?: match.value
        }
    }
}

/**
 * An [AvTag] partially rendered as `[anki:play:q:100]`
 */
data class AvRef(
    val side: String,
    val index: Int,
) {
    companion object {
        fun from(match: MatchResult): AvRef? {
            val groups = match.groupValues

            val index = groups[3].toIntOrNull() ?: return null

            val side =
                when (groups[2]) {
                    "q" -> "q"
                    "a" -> "a"
                    else -> return null
                }
            return AvRef(side, index)
        }

        val REGEX = Regex("\\[anki:(play:(.):(\\d+))]")
    }
}

/** Similar to [File.toURI], but doesn't use the absolute file to simplify testing */
@NotInPyLib
@VisibleForTesting
fun getFileUri(path: String): URI {
    var p = path
    if (File.separatorChar != '/') p = p.replace(File.separatorChar, '/')
    if (!p.startsWith("/")) p = "/$p"
    if (!p.startsWith("//")) p = "//$p"
    return URI("file", p, null)
}
