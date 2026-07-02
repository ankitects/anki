// SPDX-FileCopyrightText: 2026 lukstbit <52494258+lukstbit@users.noreply.github.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.dialogs.viewmodel

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.dialogs.ExportReadyDialog
import kotlinx.coroutines.flow.StateFlow
import kotlinx.parcelize.Parcelize
import timber.log.Timber

/**
 * [ViewModel] that implements actions related to the final part of exporting apkg/text in the base
 * [AnkiActivity] class
 * @see ExportReadyDialog
 * @see AnkiActivity
 */
class ExportReadyViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val exportReadyDestination: StateFlow<ExportReadyParams?>
        field = savedStateHandle.getMutableStateFlow(ARG_EXPORT_READY_PARAMS, null)

    fun registerExportReadyRequest(params: ExportReadyParams) {
        Timber.d("Export ready dialog requested")
        savedStateHandle[ARG_EXPORT_READY_PARAMS] = params
    }

    fun clearExportReadyRequest() {
        Timber.d("Clearing export ready dialog request")
        savedStateHandle[ARG_EXPORT_READY_PARAMS] = null
    }

    /** Encapsulates the data needed to start a new instance of [ExportReadyDialog] */
    @Parcelize
    data class ExportReadyParams(
        val exportPath: String,
        val asText: Boolean = false,
    ) : Parcelable

    companion object {
        private const val ARG_EXPORT_READY_PARAMS = "arg_export_ready_params"
    }
}
