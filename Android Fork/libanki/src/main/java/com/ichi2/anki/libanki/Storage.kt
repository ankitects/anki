// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>

package com.ichi2.anki.libanki

import com.ichi2.anki.common.time.Time
import com.ichi2.anki.common.time.TimeManager.time
import com.ichi2.anki.common.time.getDayStart
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import java.io.File

object Storage {
    /**
     *  Open a new or existing collection.
     *
     * @param collectionFiles: The files of this collection
     * Files should be tested with [File.exists] and [File.canWrite] before this is called.
     * */
    fun collection(
        collectionFiles: CollectionFiles,
        databaseBuilder: (Backend) -> DB,
        backend: Backend? = null,
    ): Collection {
        val backend2 = backend ?: BackendFactory.getBackend()
        return Collection(
            collectionFiles = collectionFiles,
            databaseBuilder = databaseBuilder,
            backend = backend2,
        )
    }

    /**
     * Called as part of [Collection] initialization. Don't call directly.
     */
    internal fun openDB(
        args: OpenDbArgs,
        backend: Backend,
        afterFullSync: Boolean,
        buildDatabase: (Backend) -> DB,
    ): Pair<DB, Boolean> {
        var create = args.isNewDatabase()
        if (afterFullSync) {
            create = false
        } else {
            backend.openCollection(collectionPath = args.collectionPath)
        }
        val db = buildDatabase(backend)

        // initialize
        if (create) {
            createDB(db, time)
        }
        return Pair(db, create)
    }

    private fun createDB(
        db: DB,
        time: Time,
    ) {
        // This line is required for testing - otherwise Rust will override a mocked time.
        db.execute("update col set crt = ?", getDayStart(time) / 1000)
    }

    sealed class OpenDbArgs {
        data class Path(
            val path: File,
        ) : OpenDbArgs()

        data object InMemory : OpenDbArgs()

        fun isNewDatabase(): Boolean =
            when (this) {
                is InMemory -> true
                is Path -> !path.exists()
            }

        val collectionPath: String
            get() =
                when (this) {
                    is InMemory -> ":memory:"
                    is Path -> path.absolutePath
                }
    }
}
