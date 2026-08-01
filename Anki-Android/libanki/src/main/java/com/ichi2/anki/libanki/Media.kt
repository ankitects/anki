/*
 * Copyright (c) 2011 Kostas Spyropoulos <inigo.aldana@gmail.com>
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>
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

package com.ichi2.anki.libanki

import androidx.annotation.WorkerThread
import anki.media.CheckMediaResponse
import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteString
import com.ichi2.anki.libanki.Media.Companion.htmlMediaRegexps
import com.ichi2.anki.libanki.exception.EmptyMediaException
import com.ichi2.anki.libanki.utils.LibAnkiAlias
import com.ichi2.anki.libanki.utils.NotInPyLib
import net.ankiweb.rsdroid.RustCleanup
import timber.log.Timber
import java.io.File

/**
 * Media manager - handles the addition and removal of media files from the media directory (collection.media) and
 * maintains the media database, which is used to determine the state of files for syncing.
 */
@WorkerThread
open class Media(
    private val col: Collection,
) {
    /**
     * The on-disk `collection.media` folder
     *
     * @throws UnsupportedOperationException The collection is in-memory
     */
    val dir: File by lazy {
        // Tests are in-memory with no disk access by default
        // `lazy` enables initialization of the class, failing when the media folder is required
        col.requireMediaFolder()
    }

    init {
        Timber.v("dir %s", col.mediaFolder)
        col.mediaFolder?.let {
            if (!it.exists()) {
                Timber.i("Creating collection.media")
                it.mkdirs()
            }
        }
    }

    /*
      Adding media
     ***********************************************************
     */

    /**
     * Add [filename][File.name] to the media folder, renaming if not unique
     *
     * @return possibly-renamed filename
     *
     * @throws EmptyMediaException if [file] is empty
     * @throws OutOfMemoryError if the file could not be copied to a contiguous block of memory (or is >= 2GB)
     */
    @LibAnkiAlias("add_file")
    @RustCleanup("use backend exception instead of EmptyMediaException")
    fun addFile(file: File): String {
        // fail if non-existing or empty
        if (file.length() == 0L) throw EmptyMediaException()
        return writeData(file.name, file.readBytes().toByteString())
    }

    /**
     * Write the file to the media folder, renaming if not unique
     *
     * @return possibly-renamed filename
     *
     * @throws OutOfMemoryError if the file could not be copied to a contiguous block of memory (or is >= 2GB)
     */
    @LibAnkiAlias("write_data")
    fun writeData(
        desiredFname: String,
        data: ByteString,
    ): String =
        col.backend.addMediaFile(
            desiredName = desiredFname,
            data = data,
        )

    /*
     * String manipulation
     * ***********************************************************
     */

    /**
     * Extract media filenames from an HTML string.
     *
     * @param currentCard The card to scan for media filenames ([sound:...] or <img...>).
     * @return A distinct, unordered list containing all the sound and image filenames found in the card.
     */
    // FIXME This doesn't match the parameters of the pylib's method
    @LibAnkiAlias("files_in_str")
    fun filesInStr(
        currentCard: Card,
        includeRemote: Boolean = false,
    ): List<String> {
        val files = mutableListOf<String>()
        val model = currentCard.note(col).notetype
        // handle latex
        val renderOutput = currentCard.renderOutput(col)
        val processedText = renderLatex(renderOutput.questionText + renderOutput.answerText, model, col)
        // extract filenames
        for (pattern in htmlMediaRegexps) {
            val matches = pattern.findAll(processedText)
            for (match in matches) {
                val fname = pattern.extractFilename(match) ?: continue
                val isLocal = !Regex("(?i)https?|ftp://").containsMatchIn(fname)
                if (isLocal || includeRemote) {
                    files.add(fname)
                }
            }
        }

        // not in libAnki: the rendered output no longer contains [sound:] tags
        files.addAll(
            (renderOutput.questionAvTags + renderOutput.answerAvTags)
                .filterIsInstance<SoundOrVideoTag>()
                .map { it.filename },
        )
        return files.distinct()
    }

    fun findUnusedMediaFiles(): List<File> = check().unusedList.map { File(dir, it) }

    /**
     * [IRI](https://en.wikipedia.org/wiki/Internationalized_Resource_Identifier) encodes media
     *
     * `foo bar` -> `foo%20bar`
     */
    fun escapeMediaFilenames(
        string: String,
        unescape: Boolean = false,
    ): String =
        if (unescape) {
            col.backend.decodeIriPaths(string)
        } else {
            col.backend.encodeIriPaths(string)
        }

    /*
      Rebuilding DB
     ***********************************************************
     */
    fun check(): CheckMediaResponse = col.backend.checkMedia()

    @LibAnkiAlias("empty_trash")
    fun emptyTrash() = col.backend.emptyTrash()

    /**
     * Copying on import
     * ***********************************************************
     */
    open fun have(fname: String): Boolean = File(dir, fname).exists()

    open fun forceResync() {
        col.backend.removeMediaDb(colPath = col.colDb.absolutePath)
    }

    // FIXME: this currently removes files immediately, as the UI does not expose a way

    /** Move provided files to the trash. */
    @LibAnkiAlias("trash_files")
    fun trashFiles(fnames: List<String>) {
        Timber.i("Deleting %d file(s) from %s", fnames.size, dir)
        col.backend.trashMediaFiles(fnames = fnames)
    }

    @LibAnkiAlias("restore_trash")
    fun restoreTrash() = col.backend.restoreTrash()

    companion object {
        /**
         * Given a [media regex][htmlMediaRegexps] and a match, return the captured media filename
         */
        @NotInPyLib
        private fun Regex.extractFilename(match: MatchResult): String? {
            val index =
                when (htmlMediaRegexps.indexOf(this)) {
                    0, 2 -> 3
                    1, 3 -> 2
                    else -> throw IllegalStateException(pattern)
                }
            return match.groups[index]?.value
        }

        val htmlMediaRegexps =
            listOf(
                // src element quoted case
                Regex("(?i)(<(?:img|audio|source)\\b[^>]* src=(['\"])([^>]+?)\\2[^>]*>)"), // Group 3 = fname
                // unquoted src (img/audio)
                Regex("(?i)(<(?:img|audio|source)\\b[^>]* src=(?!['\"])([^ >]+)[^>]*?>)"), // Group 2 = fname
                // quoted data attribute
                Regex("(?i)(<object\\b[^>]* data=(['\"])([^>]+?)\\2[^>]*>)"), // Group 3 = fname
                // unquoted data attribute
                Regex("(?i)(<object\\b[^>]* data=(?!['\"])([^ >]+)[^>]*?>)"), // Group 2 = fname
            )
    }
}
