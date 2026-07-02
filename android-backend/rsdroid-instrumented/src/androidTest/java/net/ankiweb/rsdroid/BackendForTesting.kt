/*
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package net.ankiweb.rsdroid

import androidx.annotation.VisibleForTesting

class BackendForTesting(
    langs: Iterable<String>,
) : Backend(langs) {
    // Debug methods
    /**
     * Throws a given error, generated from the Rust
     * @param error The error to throw
     */
    @VisibleForTesting
    fun debugProduceError(error: ErrorType) {
        super.debugProduceError(error.toString())
        throw IllegalStateException("An exception should have been thrown")
    }

    enum class ErrorType {
        InvalidInput,
        TemplateError,
        DbErrorFileTooNew,
        DbErrorFileTooOld,
        DbErrorMissingEntity,
        DbErrorCorrupt,
        DbErrorLocked,
        DbErrorOther,
        NetworkError,
        SyncErrorAuthFailed,
        SyncErrorOther,
        SyncErrorServerMessage,
        JSONError,
        ProtoError,
        Interrupted,
        CollectionNotOpen,
        CollectionAlreadyOpen,
        NotFound,
        Existing,
        FilteredDeckError,
        SearchError,
        FatalError,
    }

    companion object {
        fun create(): BackendForTesting {
            System.loadLibrary("rsdroid")
            return BackendForTesting(mutableListOf("en"))
        }
    }
}
