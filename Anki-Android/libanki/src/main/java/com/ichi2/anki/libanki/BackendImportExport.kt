/*
 * Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.libanki

import anki.import_export.ExportLimit
import anki.import_export.exportAnkiPackageOptions
import net.ankiweb.rsdroid.Backend

/**
 * Replace the collection file with the one in the provided .colpkg file.
 * The collection must be already closed, and must be opened afterwards.
 * */
fun importCollectionPackage(
    backend: Backend,
    collectionFiles: CollectionFiles,
    colpkgPath: String,
) {
    val col = collectionFiles.requireDiskBasedCollection()
    backend.importCollectionPackage(
        colPath = col.colDb.absolutePath,
        backupPath = colpkgPath,
        mediaFolder = col.mediaFolder.absolutePath,
        mediaDb = col.mediaDb.absolutePath,
    )
}

fun Collection.getImportAnkiPackagePresetsRaw(input: ByteArray): ByteArray = backend.getImportAnkiPackagePresetsRaw(input)

/**
 * Export the specified deck to an .apkg file.
 * * If legacy is false, an apkg will be created that can only
 * be opened with recent Anki versions.
 */
fun Collection.exportAnkiPackage(
    outPath: String,
    withScheduling: Boolean,
    withDeckConfigs: Boolean,
    withMedia: Boolean,
    limit: ExportLimit,
    legacy: Boolean,
) {
    val options =
        exportAnkiPackageOptions {
            this.withScheduling = withScheduling
            this.withMedia = withMedia
            this.legacy = legacy
            this.withDeckConfigs = withDeckConfigs
        }
    backend.exportAnkiPackage(outPath, options, limit)
}

fun Collection.exportNotesCsv(
    outPath: String,
    withHtml: Boolean,
    withTags: Boolean,
    withDeck: Boolean,
    withNotetype: Boolean,
    withGuid: Boolean,
    limit: ExportLimit,
) {
    backend.exportNoteCsv(outPath, withHtml, withTags, withDeck, withNotetype, withGuid, limit)
}

fun Collection.exportCardsCsv(
    outPath: String,
    withHtml: Boolean,
    limit: ExportLimit,
) {
    backend.exportCardCsv(outPath, withHtml, limit)
}

fun Collection.getCsvMetadataRaw(input: ByteArray): ByteArray = backend.getCsvMetadataRaw(input)

fun Collection.importCsvRaw(input: ByteArray): ByteArray = backend.importCsvRaw(input)
