/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
package com.ichi2.anki.utils.ext

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import com.ichi2.anki.CollectionHelper
import java.io.File
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Run a [block] on a [TypedArray] receiver that is recycled at the end.
 * @return The return value of the block.
 *
 * @see android.content.res.Resources.Theme.obtainStyledAttributes
 * @see androidx.core.content.withStyledAttributes
 */
@OptIn(ExperimentalContracts::class)
inline fun <T> Context.usingStyledAttributes(
    set: AttributeSet?,
    attrs: IntArray,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0,
    block: TypedArray.() -> T,
): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    val typedArray = obtainStyledAttributes(set, attrs, defStyleAttr, defStyleRes)
    return typedArray.block().also { typedArray.recycle() }
}

/**
 * Encapsulates the dimensions of a [Bitmap].
 * Note: width and height might be set to -1 if there are errors when decoding the file.
 */
data class BitmapSize(
    val width: Int,
    val height: Int,
)

/**
 * Calculate the size of a [Bitmap] from [com.ichi2.anki.libanki.Collection].
 * @param filename the full name of the [Bitmap] file
 * @return a [BitmapSize] containing the calculated dimensions or null if the file was not found in
 * the collection directory
 */
fun Context.getSizeOfBitmapFromCollection(filename: String): BitmapSize? {
    val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(this)
    val destFile = File(currentAnkiDroidDirectory, filename)
    if (!destFile.exists()) return null
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // returned bitmap will be null, we just calculate the size
    BitmapFactory.decodeFile(destFile.absolutePath, opts)
    return BitmapSize(opts.outWidth, opts.outHeight)
}
