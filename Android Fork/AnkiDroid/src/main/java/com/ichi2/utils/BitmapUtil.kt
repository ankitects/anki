// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2013 Bibek Shrestha <bibekshrestha@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>

package com.ichi2.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.annotation.CheckResult
import com.ichi2.anki.common.crashreporting.CrashReportService
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.lang.Exception
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

object BitmapUtil {
    fun decodeFile(
        theFile: File,
        imageMaxSize: Int,
    ): Bitmap? {
        var bmp: Bitmap? = null
        try {
            if (!theFile.exists()) {
                Timber.i("not displaying preview - image does not exist: '%s'", theFile.path)
                return null
            }
            // Decode image size
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            FileInputStream(theFile).use { fis ->
                BitmapFactory.decodeStream(fis, null, o)
            }
            var scale = 1
            if (o.outHeight > imageMaxSize || o.outWidth > imageMaxSize) {
                scale =
                    2.0
                        .pow(
                            (
                                ln(imageMaxSize / o.outHeight.coerceAtLeast(o.outWidth).toDouble()) /
                                    ln(0.5)
                            ).roundToInt().toDouble(),
                        ).toInt()
            }

            // Decode with inSampleSize
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = scale
            FileInputStream(theFile).use { fis ->
                bmp = BitmapFactory.decodeStream(fis, null, o2)
            }
        } catch (e: Exception) {
            // #5513 - We don't know the reason for the crash, let's find out.
            CrashReportService.sendExceptionReport(e, "BitmapUtil decodeFile")
        }
        return bmp
    }

    fun freeImageView(imageView: ImageView?) {
        // This code behaves differently on various OS builds. That is why put into try catch.
        try {
            if (imageView != null) {
                @Suppress("UNUSED_VARIABLE")
                val dr = (imageView.drawable ?: return) as? BitmapDrawable ?: return
                val bd = imageView.drawable as BitmapDrawable
                if (bd.bitmap != null) {
                    bd.bitmap.recycle()
                    imageView.setImageBitmap(null)
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    /**
     * Decodes a file, downsampling it to fit within the reqWidth.
     */
    @CheckResult
    fun decodeSampledBitmap(
        file: File,
        reqWidth: Int,
    ): Bitmap? {
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, options)

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth)

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    /**
     * Calculate the largest inSampleSize value that is a power of 2 and keeps
     * width larger than the requested width.
     *
     * @param options The Options object, which must have been populated by a previous
     * decoding call with inJustDecodeBounds=true.
     * @param reqWidth The target width to fit the image into.
     */
    @CheckResult
    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
    ): Int {
        // Raw width of image
        val width = options.outWidth
        var inSampleSize = 1

        if (width <= reqWidth) {
            return 1
        }
        val halfWidth = width / 2
        while ((halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
