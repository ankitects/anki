// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

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
