// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import com.ichi2.anki.common.storage.CollectionHelper
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
