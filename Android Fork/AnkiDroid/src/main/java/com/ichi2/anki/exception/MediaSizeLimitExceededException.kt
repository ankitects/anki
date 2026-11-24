// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ujjol Chakrabarty <ujjol.uc@gmail.com>

package com.ichi2.anki.exception

import android.content.Context

fun Long.toBytesShortString(context: Context): String =
    android.text.format.Formatter
        .formatShortFileSize(context, this)

class MediaSizeLimitExceededException(
    val fileName: String,
    val fileSize: Long,
    val limit: Long,
) : Exception("Media size limit exceeded: $fileName ($fileSize > $limit)")
