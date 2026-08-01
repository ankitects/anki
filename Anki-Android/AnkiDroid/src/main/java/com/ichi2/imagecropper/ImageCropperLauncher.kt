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

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.ichi2.anki.SingleFragmentActivity

/**
 * Launches the ImageCropper fragment with different configurations.
 */
sealed interface ImageCropperLauncher {
    /**
     * Generates an intent to open the ImageCropper fragment with the configured parameters.
     *
     * @param context The context from which the intent is launched.
     * @return Intent configured to launch the ImageCropper fragment.
     */
    fun getIntent(context: Context) = SingleFragmentActivity.getIntent(context, ImageCropper::class, toBundle())

    /**
     * Converts the configuration into a Bundle to pass arguments to the ImageCropper fragment.
     *
     * @return Bundle containing arguments specific to this configuration.
     */
    fun toBundle(): Bundle

    /**
     * Represents opening the ImageCropper with an image URI.
     * @property imageUri The URI of the image to crop.
     */
    data class ImageUri(
        val imageUri: Uri?,
    ) : ImageCropperLauncher {
        override fun toBundle(): Bundle =
            Bundle().apply {
                putParcelable(ImageCropper.CROP_IMAGE_URI, imageUri)
            }
    }
}
