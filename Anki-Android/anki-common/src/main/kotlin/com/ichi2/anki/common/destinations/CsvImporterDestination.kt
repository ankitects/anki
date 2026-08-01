// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.common.destinations

/** Opens the CSV importer for the file at [filePath], which must be accessible by AnkiDroid. */
data class CsvImporterDestination(
    val filePath: String,
) : Destination()
