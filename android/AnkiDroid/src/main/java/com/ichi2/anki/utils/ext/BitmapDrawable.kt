/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 * Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.utils.ext

import android.graphics.Bitmap.CompressFormat
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import android.webkit.WebView
import java.io.ByteArrayOutputStream

/**
 * Converts a [BitmapDrawable] into a Base64 PNG, typically for displaying in a [WebView]
 */
fun BitmapDrawable.toBase64Png() =
    ByteArrayOutputStream().use { outputStream ->
        // quality (100) is ignored as PNG is lossless
        bitmap.compress(CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        Base64.encodeToString(byteArray, Base64.DEFAULT)
    }
