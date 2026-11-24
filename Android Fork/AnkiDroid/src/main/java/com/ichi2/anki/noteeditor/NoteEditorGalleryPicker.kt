// SPDX-FileCopyrightText: 2026 David Allison <62114487+david-allison@users.noreply.github.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.ichi2.anki.CommonString
import com.ichi2.anki.multimedia.PickedImage
import com.ichi2.anki.snackbar.showSnackbar
import timber.log.Timber

/**
 * Picks an image before opening the image editor, retaining the target note field across recreation.
 * Must be constructed during fragment initialization to register its activity result launcher.
 */
internal class NoteEditorGalleryPicker(
    private val fragment: Fragment,
    onImagePicked: (fieldIndex: Int, imageUri: Uri) -> Unit,
) {
    private var pendingFieldIndex: Int? = null

    private val launcher =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val fieldIndex = pendingFieldIndex ?: return@registerForActivityResult
            pendingFieldIndex = null
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

            val imageUri = result.data?.let { PickedImage(it).trustedUri }
            if (imageUri == null) {
                fragment.showSnackbar(CommonString.select_image_failed)
                return@registerForActivityResult
            }
            onImagePicked(fieldIndex, imageUri)
        }

    fun launch(fieldIndex: Int) {
        pendingFieldIndex = fieldIndex
        try {
            launcher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
        } catch (e: ActivityNotFoundException) {
            pendingFieldIndex = null
            Timber.w(e, "No app found to select image")
            fragment.showSnackbar(CommonString.activity_start_failed)
        }
    }

    fun onSaveInstanceState(outState: Bundle) {
        pendingFieldIndex?.let { outState.putInt(STATE_KEY_FIELD_INDEX, it) }
    }

    fun onRestoreInstanceState(state: Bundle) {
        pendingFieldIndex = state.getInt(STATE_KEY_FIELD_INDEX, -1).takeIf { it >= 0 }
    }

    companion object {
        private const val STATE_KEY_FIELD_INDEX = "galleryFieldIndex"
    }
}
