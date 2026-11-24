// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.net.Uri

/** Metadata resolved once from an import intent, before copying or opening import UI. */
sealed class ImportResolution {
    data class Source(
        val uri: Uri,
        val fileName: String,
    )

    sealed class ResolvedFile(
        open val source: Source,
    ) : ImportResolution()

    data class DeckPackage(
        override val source: Source,
    ) : ResolvedFile(source)

    data class CollectionPackage(
        override val source: Source,
    ) : ResolvedFile(source)

    data class Text(
        override val source: Source,
    ) : ResolvedFile(source)

    data class Failure(
        val error: ImportResult.Failure,
    ) : ImportResolution()
}
