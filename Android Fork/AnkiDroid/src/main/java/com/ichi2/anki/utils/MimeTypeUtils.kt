// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 argon2r <vincentcs008@gmail.com>

package com.ichi2.anki.utils

object MimeTypeUtils {
    val APKG_MIME_TYPES = setOf("application/apkg", "application/x-apkg", "application/vnd.anki")
    val COLPKG_MIME_TYPES = setOf("application/colpkg", "application/x-colpkg")

    val CSV_TSV_MIME_TYPES =
        arrayOf(
            "text/comma-separated-values",
            "text/csv",
            "text/tab-separated-values",
            "text/tsv",
            "text/plain",
        )
}
