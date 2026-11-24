// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.destinations

/** Opens the CSV importer for the file at [filePath], which must be accessible by AnkiDroid. */
data class CsvImporterDestination(
    val filePath: String,
) : Destination()
