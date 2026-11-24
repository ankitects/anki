// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import anki.notes.NoteFieldsCheckResponse
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.NoteFieldsCheckResult.Failure
import com.ichi2.anki.NoteFieldsCheckResult.Success
import com.ichi2.anki.libanki.Note
import timber.log.Timber

/**
 * The result of [checking note fields][Note.fieldsCheck], either a [Success], or [Failure] with
 * user-readable message
 *
 * @see NoteFieldsCheckResponse.State
 * @see checkNoteFieldsResponse
 */
sealed interface NoteFieldsCheckResult {
    data object Success : NoteFieldsCheckResult

    /** @property localizedMessage user-readable error message */
    data class Failure(
        val localizedMessage: String?,
    ) : NoteFieldsCheckResult
}

/**
 * Validates fields of a note; produces a human-readable error on failure
 *
 * @see NoteFieldsCheckResult
 * @see Note.fieldsCheck
 **/
suspend fun checkNoteFieldsResponse(note: Note): NoteFieldsCheckResult {
    Timber.d("validating note fields")
    val fieldsCheckState = withCol { note.fieldsCheck(this) }

    return when (fieldsCheckState) {
        NoteFieldsCheckResponse.State.NORMAL, NoteFieldsCheckResponse.State.DUPLICATE,
        -> Success

        NoteFieldsCheckResponse.State.EMPTY ->
            if (note.notetype.isImageOcclusion) {
                Failure(TR.notetypesNoOcclusionCreated2())
            } else {
                Failure(TR.addingTheFirstFieldIsEmpty())
            }

        NoteFieldsCheckResponse.State.MISSING_CLOZE ->
            Failure(TR.addingYouHaveAClozeDeletionNote())

        NoteFieldsCheckResponse.State.NOTETYPE_NOT_CLOZE ->
            Failure(TR.addingClozeOutsideClozeNotetype())

        NoteFieldsCheckResponse.State.FIELD_NOT_CLOZE ->
            Failure(TR.addingClozeOutsideClozeField())

        NoteFieldsCheckResponse.State.UNRECOGNIZED ->
            Failure(localizedMessage = null)
    }
}
