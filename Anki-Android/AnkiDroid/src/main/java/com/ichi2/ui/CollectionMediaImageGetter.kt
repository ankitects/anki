/*
 * Copyright (c) 2025 Rakshita Chauhan <chauhanrakshita64@gmail.com>
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
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.LevelListDrawable
import android.text.Html
import android.widget.TextView
import androidx.annotation.CheckResult
import androidx.core.graphics.drawable.toDrawable
import com.ichi2.utils.BitmapUtil.decodeSampledBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.lang.ref.WeakReference

/**
 * Async ImageGetter for Deck Descriptions.
 * Resolves local images from the collection media directory and downsamples them to prevent OOM.
 */
class CollectionMediaImageGetter(
    private val context: Context,
    view: TextView,
    private val mediaDir: File,
    private val scope: CoroutineScope,
) : Html.ImageGetter {
    private val containerRef = WeakReference(view)
    private var imageCount = 0

    override fun getDrawable(source: String): Drawable {
        // Return a transparent placeholder
        val wrapper = createWrapper()

        // limit the number of images loaded to prevent ANRs/OOMs
        if (imageCount >= MAX_IMAGE_COUNT) {
            return wrapper
        }
        imageCount++

        scope.launch {
            val bitmap = loadBitmap(source) ?: return@launch
            updateWrapper(wrapper, bitmap)
        }

        return wrapper
    }

    private fun createWrapper(): LevelListDrawable {
        val wrapper = LevelListDrawable()
        val empty = Color.TRANSPARENT.toDrawable()
        wrapper.addLevel(0, 0, empty)
        wrapper.setBounds(0, 0, 0, 0)
        return wrapper
    }

    @CheckResult
    private suspend fun loadBitmap(source: String): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                // Skip remote images
                if (source.startsWith("http://") || source.startsWith("https://")) {
                    return@withContext null
                }

                val localFile = File(mediaDir, source)

                // Prevent path traversal to ensure file is inside media directory
                if (!localFile.canonicalPath.startsWith(mediaDir.canonicalPath + File.separator)) {
                    Timber.w("CollectionMediaImageGetter: Path traversal attempt detected: %s", source)
                    return@withContext null
                }

                if (localFile.exists()) {
                    // Use view width or fallback to screen width
                    val reqWidth =
                        (containerRef.get()?.width ?: 0)
                            .coerceAtLeast(context.resources.displayMetrics.widthPixels)
                    decodeSampledBitmap(localFile, reqWidth)
                } else {
                    null
                }
            } catch (e: Throwable) {
                Timber.w(e, "Failed to load deck description image: %s", source)
                null
            }
        }

    private fun updateWrapper(
        wrapper: LevelListDrawable,
        bitmap: Bitmap,
    ) {
        val textView =
            containerRef.get() ?: run {
                Timber.w("CollectionMediaImageGetter: TextView reference lost")
                return
            }

        val d = bitmap.toDrawable(context.resources)

        var viewWidth = textView.width
        if (viewWidth <= 0) viewWidth = context.resources.displayMetrics.widthPixels

        val scale = viewWidth.toFloat() / bitmap.width
        var w = bitmap.width
        var h = bitmap.height

        // Downscale to fit view if necessary
        if (scale < 1.0f) {
            w = viewWidth
            h = (h * scale).toInt()
        }

        d.setBounds(0, 0, w, h)
        wrapper.addLevel(1, 1, d)
        wrapper.setBounds(0, 0, w, h)
        wrapper.level = 1

        // Force a re-layout to fit the new image dimensions
        textView.text = textView.text
    }

    companion object {
        private const val MAX_IMAGE_COUNT = 10
    }
}
