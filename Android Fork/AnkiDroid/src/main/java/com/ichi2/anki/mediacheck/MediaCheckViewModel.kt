// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.mediacheck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import anki.media.CheckMediaResponse
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.progress.HasProgress
import com.ichi2.anki.progress.ProgressManager
import com.ichi2.anki.utils.MessageQueue
import com.ichi2.anki.utils.MessageQueue.MessageId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** The operation [MediaCheckViewModel] is running, shown as a progress message by the UI. */
enum class MediaCheckProgress {
    CHECKING_MEDIA,
    ADDING_TAGS,
    DELETING_MEDIA,
}

/** The outcome of a [MediaCheckViewModel] operation, which the UI shows and then acknowledges. */
sealed interface MediaCheckMessage {
    data class TagsAdded(
        val notesUpdated: Int,
    ) : MediaCheckMessage

    data class MediaDeleted(
        val count: Int,
    ) : MediaCheckMessage

    data object TrashEmptied : MediaCheckMessage

    data object TrashRestored : MediaCheckMessage

    data class Failed(
        val exception: Exception,
    ) : MediaCheckMessage
}

@NeedsTest("Test the media check process i.e. the buttons and views")
class MediaCheckViewModel :
    ViewModel(),
    HasProgress<MediaCheckProgress> {
    override val progressManager = ProgressManager<MediaCheckProgress>()

    val mediaCheckResult: StateFlow<CheckMediaResponse?>
        field = MutableStateFlow<CheckMediaResponse?>(null)

    private val messageQueue = MessageQueue<MediaCheckMessage>()

    val pendingMessages = messageQueue.messages

    fun messageShown(id: MessageId) = messageQueue.acknowledge(id)

    fun tagMissing(tag: String): Job =
        launchOperation(MediaCheckProgress.ADDING_TAGS) {
            val taggedNotes =
                undoableOp {
                    tags.bulkAdd(mediaCheckResult.value?.missingMediaNotesList ?: listOf(), tag)
                }
            messageQueue.enqueue(MediaCheckMessage.TagsAdded(taggedNotes.count))
        }

    fun checkMedia(): Job =
        launchOperation(MediaCheckProgress.CHECKING_MEDIA) {
            mediaCheckResult.value = withCol { media.check() }
        }

    fun deleteTrash(): Job =
        launchOperation {
            withCol { media.emptyTrash() }
            messageQueue.enqueue(MediaCheckMessage.TrashEmptied)
        }

    fun restoreTrash(): Job =
        launchOperation {
            withCol { media.restoreTrash() }
            messageQueue.enqueue(MediaCheckMessage.TrashRestored)
        }

    // TODO: investigate: the underlying implementation exposes progress, which we do not yet handle.
    fun deleteUnusedMedia(): Job =
        launchOperation(MediaCheckProgress.DELETING_MEDIA) {
            val unused = mediaCheckResult.value?.unusedList ?: listOf()
            withCol { media.trashFiles(unused) }
            messageQueue.enqueue(MediaCheckMessage.MediaDeleted(unused.size))
        }

    private fun launchOperation(
        progress: MediaCheckProgress? = null,
        block: suspend () -> Unit,
    ): Job =
        viewModelScope.launch {
            try {
                progressManager.withProgress(message = progress) { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("Media check operation failed")
                messageQueue.enqueue(MediaCheckMessage.Failed(e))
            }
        }
}
