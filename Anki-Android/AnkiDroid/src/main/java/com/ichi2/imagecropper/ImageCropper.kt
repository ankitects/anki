/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
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

package com.ichi2.imagecropper

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.BundleCompat
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.CropImageView
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentImageCropperBinding
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.withProgress
import com.ichi2.imagecropper.ImageCropper.Companion.DECODED_IMAGE_LIMIT
import com.ichi2.utils.ContentResolverUtil
import com.ichi2.utils.openInputStreamSafe
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber

/**
 * Fragment for cropping images within the AnkiDroid, uses [CropImageView] to crop the image and
 * sends the image uri as result.
 *
 * Portions of this code were adapted from the CanHub project.
 * Original source: https://github.com/CanHub/Android-Image-Cropper
 *
 * Attribution to the original authors of the CanHub/Android-Image-Cropper for their contributions.
 */
class ImageCropper :
    Fragment(R.layout.fragment_image_cropper),
    MenuProvider {
    private val binding by viewBinding(FragmentImageCropperBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? AppCompatActivity)?.apply {
            setSupportActionBar(binding.toolbar)
            // there's no need for a title anyway and if we don't set it we end up with "AnkiDroid"
            // as the title which is useless
            supportActionBar?.title = ""
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
        binding.cropImageView.apply {
            setOnSetImageUriCompleteListener(::onSetImageUriComplete)
            setOnCropImageCompleteListener(::onCropImageComplete)
            cropRect = Rect(100, 300, 500, 1200)
        }
        val originalImageUri =
            BundleCompat.getParcelable(requireArguments(), CROP_IMAGE_URI, Uri::class.java)
                ?: error("No image identifier was provided for cropping")
        viewLifecycleOwner.lifecycleScope.launch {
            withProgress {
                if (isImageTooBig(originalImageUri)) {
                    binding.cropImageSizeNotice.isVisible = true
                } else {
                    binding.cropImageView.setImageUriAsync(originalImageUri)
                }
            }
        }

        requireActivity().addMenuProvider(this, viewLifecycleOwner)
    }

    /**
     * Check if the image isn't too big for the crop editor to handle(issue #17378).
     * @see DECODED_IMAGE_LIMIT
     * @return true if the image is bigger than our general target limit, false otherwise(or for any error)
     */
    private fun isImageTooBig(imageUri: Uri): Boolean =
        try {
            val imageStream = requireContext().contentResolver.openInputStreamSafe(imageUri)
            if (imageStream != null) {
                val opts = BitmapFactory.Options()
                opts.inJustDecodeBounds = true
                BitmapFactory.decodeStream(imageStream, null, opts)
                val imageDimen = opts.outWidth * opts.outHeight * 4 // Bitmap.Config.ARGB_8888
                Timber.d("Crop target image size: $imageDimen")
                imageDimen > DECODED_IMAGE_LIMIT
            } else {
                false
            }
        } catch (ex: Exception) {
            false
        }

    override fun onCreateMenu(
        menu: Menu,
        menuInflater: MenuInflater,
    ) {
        menuInflater.inflate(R.menu.image_cropper_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
        when (menuItem.itemId) {
            R.id.action_done -> {
                Timber.d("Done clicked")
                val imageFormat = binding.cropImageView.imageUri?.let { getImageCompressFormat(it) }
                Timber.d("Compress format: $imageFormat")
                if (imageFormat != null) {
                    binding.cropImageView.croppedImageAsync(
                        saveCompressFormat = imageFormat,
                    )
                }
                true
            }

            R.id.action_rotate -> {
                Timber.d("Rotate clicked")
                binding.cropImageView.rotateImage(90)
                true
            }

            R.id.action_flip_horizontally -> {
                Timber.d("Flip horizontally clicked")
                binding.cropImageView.flipImageHorizontally()
                true
            }

            R.id.action_flip_vertically -> {
                Timber.d("Flip vertically clicked")
                binding.cropImageView.flipImageVertically()
                true
            }

            else -> false
        }

    private fun getImageCompressFormat(uri: Uri): Bitmap.CompressFormat {
        Timber.d("Original image URI: $uri")

        val fileExtension =
            try {
                ContentResolverUtil.getFileName(requireContext().contentResolver, uri).substringAfterLast('.')
            } catch (e: Exception) {
                Timber.w(e, "Failed to retrieve file extension from URI")
                null
            }

        return when (fileExtension?.lowercase()) {
            "png" -> Bitmap.CompressFormat.PNG
            "jpeg", "jpg" -> Bitmap.CompressFormat.JPEG
            "webp" -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    Bitmap.CompressFormat.WEBP
                }
            }
            else -> {
                Timber.w("Unknown image format: $fileExtension. Defaulting to JPEG.")
                Bitmap.CompressFormat.JPEG
            }
        }
    }

    private fun onSetImageUriComplete(
        @Suppress("UNUSED_PARAMETER") view: CropImageView,
        @Suppress("UNUSED_PARAMETER") uri: Uri,
        error: Exception?,
    ) {
        if (error != null) {
            Timber.e(error, "Failed to load image by URI")
            showSnackbar(R.string.something_wrong)
        }
    }

    private fun onCropImageComplete(
        @Suppress("UNUSED_PARAMETER") view: CropImageView,
        result: CropImageView.CropResult,
    ) {
        if (result.error == null) {
            val resultIntent = Intent()
            resultIntent.putExtra(
                CROP_IMAGE_RESULT,
                CropResultData(
                    uriContent = result.uriContent,
                    uriPath = context?.let { result.getUriFilePath(it) },
                ),
            )
            activity?.setResult(Activity.RESULT_OK, resultIntent)
            activity?.finish()
        } else {
            Timber.e(result.error, "Failed to crop image")
            showSnackbar(R.string.something_wrong)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.cropImageView.setOnSetImageUriCompleteListener(null)
        binding.cropImageView.setOnCropImageCompleteListener(null)
    }

    companion object {
        private const val DECODED_IMAGE_LIMIT = 100_000_000

        /**
         * The key for the original image URI passed as an argument.
         */
        const val CROP_IMAGE_URI = "image_uri"

        /**
         * The key for the cropped image path sent back in the result Intent.
         */
        const val CROP_IMAGE_RESULT = "crop_image_result"
    }

    @Parcelize
    data class CropResultData(
        val uriContent: Uri? = null,
        val uriPath: String? = null,
    ) : Parcelable
}
