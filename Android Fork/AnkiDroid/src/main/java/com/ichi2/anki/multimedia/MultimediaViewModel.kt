// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multimedia

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class MultimediaViewModel : ViewModel() {
    /**
     * The option chosen in [MultimediaBottomSheet]. It has no replay, so a choice is dropped
     * unless something is already collecting.
     */
    val multimediaAction = MutableSharedFlow<MultimediaBottomSheet.MultimediaAction>()

    private var prevMultimediaPath: File? = null
    private var prevMultimediaUri: Uri? = null

    val currentMultimediaUri: StateFlow<Uri?>
        field = MutableStateFlow<Uri?>(null)

    val currentMultimediaPath: StateFlow<File?>
        field = MutableStateFlow<File?>(null)

    var selectedMediaFileSize: Long = 0

    fun setMultimediaAction(action: MultimediaBottomSheet.MultimediaAction) {
        viewModelScope.launch {
            multimediaAction.emit(action)
        }
    }

    fun saveMultimediaForRevert(
        imagePath: File?,
        imageUri: Uri?,
    ) {
        prevMultimediaPath = imagePath
        prevMultimediaUri = imageUri
    }

    fun restoreMultimedia() {
        currentMultimediaUri.value = prevMultimediaUri
        currentMultimediaPath.value = prevMultimediaPath
    }

    fun updateMediaFileLength(length: Long) {
        selectedMediaFileSize = length
    }

    fun updateCurrentMultimediaUri(uri: Uri?) {
        currentMultimediaUri.value = uri
    }

    fun updateCurrentMultimediaPath(uri: String) {
        updateCurrentMultimediaPath(File(uri))
    }

    fun updateCurrentMultimediaPath(path: File?) {
        currentMultimediaPath.value = path
    }
}
