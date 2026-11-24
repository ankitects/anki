// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.app.Activity
import android.content.Intent
import android.os.Parcelable
import androidx.activity.result.ActivityResult
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.CommonString
import com.ichi2.anki.utils.ViewModelSavedStateHandle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.io.File
import java.io.IOException

/** Retains only the external-edit operation, including results delivered during recreation. */
internal class ExternalImageEditorViewModel(
    private val savedState: ViewModelSavedStateHandle,
    val editor: ExternalImageEditor,
) : ViewModel() {
    val state: StateFlow<Edit?> = savedState.getStateFlow("externalEdit", null)

    init {
        resumeOperation()
    }

    fun prepare(source: File) {
        if (state.value != null) return
        update(Edit.Preparing(source.absolutePath))
        resumeOperation()
    }

    fun launched() {
        val ready = state.value as? Edit.Ready ?: return
        update(Edit.Editing(ready.originalPath, ready.workingPath))
    }

    fun readResult(result: ActivityResult) {
        val edit = state.value as? Edit.Editing ?: return
        // Retain only the URI; editor extras can contain bitmaps that exceed saved-state limits.
        val pendingResult = ActivityResult(result.resultCode, result.data?.data?.let { Intent().setData(it) })
        update(Edit.Importing(edit.originalPath, edit.workingPath, pendingResult))
        resumeOperation()
    }

    fun launchFailed() {
        val edit = state.value ?: return
        update(Edit.Finished(edit.originalPath, edit.workingPath, error = CommonString.activity_start_failed))
    }

    /** The fragment has displayed the result (or error); release only the editor's working copy. */
    fun consumed() {
        val edit = state.value as? Edit.Finished ?: return
        update(null)
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { editor.clear(edit.workingPath?.let(::File)) }
    }

    /** Release files on dismissal; clearing a ViewModel alone can still be followed by restoration. */
    fun discard() {
        viewModelScope.cancel()
        val edit = state.value
        // Fragment destruction can follow ViewModel clearing, so cleanup must also work in a cancelled scope.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                editor.clear(edit?.workingPath?.let(::File))
                editor.clear((edit as? Edit.Finished)?.acceptedPath?.let(::File))
            }
        }
    }

    private fun update(edit: Edit?) {
        savedState["externalEdit"] = edit
    }

    private fun resumeOperation() {
        val edit = state.value ?: return
        if (edit !is Edit.Preparing && edit !is Edit.Importing) return
        viewModelScope.launch {
            try {
                update(
                    when (edit) {
                        is Edit.Preparing -> {
                            val prepared = editor.prepare(File(edit.originalPath))
                            Edit.Ready(edit.originalPath, prepared.file.absolutePath, prepared.intent)
                        }
                        is Edit.Importing -> {
                            val accepted = editor.readResult(File(edit.originalPath), File(edit.workingPath), edit.result)
                            Edit.Finished(
                                edit.originalPath,
                                edit.workingPath,
                                accepted?.absolutePath,
                                unchanged = edit.result.resultCode == Activity.RESULT_OK && accepted == null,
                            )
                        }
                    },
                )
            } catch (e: IOException) {
                Timber.w(e, "External image editing failed")
                update(Edit.Finished(edit.originalPath, edit.workingPath, error = CommonString.multimedia_editor_something_wrong))
            }
        }
    }

    /** Only file paths and the pending Android result are saved, never image contents. */
    @Parcelize
    sealed class Edit : Parcelable {
        abstract val originalPath: String
        open val workingPath: String? get() = null

        data class Preparing(
            override val originalPath: String,
        ) : Edit()

        data class Ready(
            override val originalPath: String,
            override val workingPath: String,
            val intent: Intent,
        ) : Edit()

        data class Editing(
            override val originalPath: String,
            override val workingPath: String,
        ) : Edit()

        data class Importing(
            override val originalPath: String,
            override val workingPath: String,
            val result: ActivityResult,
        ) : Edit()

        data class Finished(
            override val originalPath: String,
            override val workingPath: String?,
            val acceptedPath: String? = null,
            @StringRes val error: Int? = null,
            val unchanged: Boolean = false,
        ) : Edit()
    }
}
