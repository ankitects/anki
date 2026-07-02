/*
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.mediacheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.media.CheckMediaResponse
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.observability.undoableOp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@NeedsTest("Test the media check process i.e. the buttons and views")
class MediaCheckViewModel : ViewModel() {
    val mediaCheckResult: StateFlow<CheckMediaResponse?>
        field = MutableStateFlow<CheckMediaResponse?>(null)

    private val deletedFilesCount: MutableStateFlow<Int> = MutableStateFlow(0)
    private val taggedFilesCount: MutableStateFlow<Int> = MutableStateFlow(0)

    val deletedFiles: Int
        get() = deletedFilesCount.value

    val taggedFiles: Int
        get() = taggedFilesCount.value

    // TODO: Move progress notifications here
    fun tagMissing(tag: String): Job =
        viewModelScope.launch {
            val taggedNotes =
                undoableOp {
                    tags.bulkAdd(mediaCheckResult.value?.missingMediaNotesList ?: listOf(), tag)
                }
            taggedFilesCount.value = taggedNotes.count
        }

    fun checkMedia(): Job =
        viewModelScope.launch {
            val result = withCol { media.check() }
            mediaCheckResult.value = result
        }

    fun deleteTrash(): Job = viewModelScope.launch { withCol { media.emptyTrash() } }

    fun restoreTrash(): Job =
        viewModelScope.launch {
            withCol { media.restoreTrash() }
        }

    // TODO: investigate: the underlying implementation exposes progress, which we do not yet handle.
    fun deleteUnusedMedia(): Job =
        viewModelScope.launch {
            val unused = mediaCheckResult.value?.unusedList ?: listOf()
            withCol { media.trashFiles(unused) }
            deletedFilesCount.value = unused.size
        }
}
