// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki.testutils

import com.ichi2.anki.common.annotations.DuplicatedCode
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.CollectionFiles
import com.ichi2.anki.libanki.DB
import com.ichi2.anki.libanki.LibAnki
import com.ichi2.anki.libanki.Storage.collection
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import org.jetbrains.annotations.VisibleForTesting
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(TestCollectionManager::class.java)

/**
 * Trimmed down version of `com.ichi2.anki.CollectionManager` which can be used without a reference
 * to Android & AnkiDroid app logic
 */
interface TestCollectionManager {
    fun getColUnsafe(): Collection

    /**
     * Close the currently cached backend and discard it. Saves and closes the collection if open.
     */
    suspend fun discardBackend()
}

/**
 * Lightweight CollectionManager: in memory-DB and no media folders/DB
 */
@VisibleForTesting
open class InMemoryCollectionManager : TestCollectionManager {
    open val collectionFiles: CollectionFiles = CollectionFiles.InMemory

    @Suppress("DEPRECATION") // deprecation is used to limit access
    private var backend: Backend?
        get() = LibAnki.backend
        set(value) {
            LibAnki.backend = value
        }

    @Suppress("DEPRECATION") // deprecation is used to limit access
    private var collection: Collection?
        get() = LibAnki.collection
        set(value) {
            LibAnki.collection = value
        }

    fun ensureOpenInner() {
        ensureBackendInner()
        if (collection?.dbClosed == false) return
        collection =
            collection(
                collectionFiles = collectionFiles,
                databaseBuilder = { backend -> createDatabaseUsingRustBackend(backend) },
                backend = backend,
            )
    }

    private fun ensureBackendInner() {
        if (backend == null) {
            backend = BackendFactory.getBackend()
        }
    }

    override fun getColUnsafe(): Collection {
        if (collection != null) return collection!!
        ensureOpenInner()
        return collection!!
    }

    /**
     * Close the currently cached backend and discard it. Useful when enabling the V16 scheduler in the
     * dev preferences, or if the active language changes. Saves and closes the collection if open.
     */
    override suspend fun discardBackend() {
        discardBackendInner()
    }

    /** See [discardBackend]. This must only be run inside the queue. */
    private fun discardBackendInner() {
        ensureClosedInner()
        if (backend != null) {
            backend!!.close()
            backend = null
        }
    }

    private fun ensureClosedInner() {
        if (collection == null) {
            return
        }
        try {
            collection!!.close()
        } catch (exc: Exception) {
            logger.error("swallowing error on close: $exc")
        }
        collection = null
    }
}

/**
 * Wrap a Rust backend connection (which provides an SQL interface).
 * Caller is responsible for opening&closing the database.
 */
@DuplicatedCode("anki-common:createDatabaseUsingRustBackend")
fun createDatabaseUsingRustBackend(backend: Backend): DB = DB(backend)
