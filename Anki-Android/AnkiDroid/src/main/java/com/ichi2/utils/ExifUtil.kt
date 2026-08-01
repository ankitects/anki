/*
 * Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>
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

package com.ichi2.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import timber.log.Timber
import java.io.File
import java.lang.Exception

object ExifUtil {
    fun rotateFromCamera(
        theFile: File,
        bitmap: Bitmap,
    ): Bitmap =
        try {
            val exif = ExifInterface(theFile.path)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val angle =
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            val mat = Matrix()
            mat.postRotate(angle.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, mat, true)
        } catch (e: Exception) {
            Timber.w(e)
            bitmap
        }
}
