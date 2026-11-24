// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import com.ichi2.anki.common.time.TimeManager
import org.slf4j.LoggerFactory
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import kotlin.time.Duration.Companion.days

private val logger = LoggerFactory.getLogger("IdSnapshotFile")

/** A temporary snapshot of card or note IDs, independent of Android saved-state transport. */
open class IdSnapshotFile(
    path: String,
) : File(path) {
    /**
     * Reads the IDs, or returns null if the snapshot is no longer available.
     * An empty snapshot returns an empty list.
     *
     * @throws IOException if the file is corrupt or reading fails after opening it.
     */
    fun getIdsOrNull(): List<Long>? =
        try {
            readIds()
        } catch (_: FileNotFoundException) {
            logger.warn("ID snapshot is no longer available: {}", this)
            null
        }

    private fun readIds(): List<Long> =
        FileInputStream(this).use { fileStream ->
            val inputStream = DataInputStream(fileStream.buffered())
            val size = inputStream.readInt()
            // Validate before allocating: a corrupt count must not allocate an enormous list,
            // and a partial selection must never be used to perform an operation.
            if (size < 0 || fileStream.channel.size() != Int.SIZE_BYTES + size.toLong() * Long.SIZE_BYTES) {
                throw IOException("Invalid IDs file length or count: $name")
            }
            List(size) { inputStream.readLong() }
        }

    companion object {
        private const val DIRECTORY_NAME = "idsfiles"

        /** Maximum age of a saved selection, including files still referenced by saved state. */
        private val FILE_RETENTION = 7.days

        /** Creates the directory on the first write, preserving the caller's snapshot prefix. */
        fun create(
            directory: File,
            ids: List<Long>,
            prefix: String,
        ): IdSnapshotFile {
            val file = IdSnapshotFile(createTempFile(prefix, ".tmp", ensureDirectory(directory)).path)
            DataOutputStream(FileOutputStream(file)).use { outputStream ->
                outputStream.writeInt(ids.size)
                for (id in ids) {
                    outputStream.writeLong(id)
                }
            }
            return file
        }

        /** Returns the path without creating it. Cache eviction may remove snapshots at any age. */
        fun getDirectory(cacheDirectory: File): File = File(cacheDirectory, DIRECTORY_NAME)

        /** Deletes expired files without creating unused directories or removing child directories. */
        fun deleteStaleFiles(directory: File) {
            val cutoff = TimeManager.time.intTimeMS() - FILE_RETENTION.inWholeMilliseconds
            val files =
                try {
                    directory.listFiles().orEmpty()
                } catch (e: SecurityException) {
                    logger.warn("Unable to list directory: {}", directory, e)
                    emptyArray()
                }
            val deleteCount =
                files.count { file ->
                    file.withCleanupErrorHandling {
                        file.isStale(cutoff) && file.deleteWithLogging()
                    }
                }
            logger.info("Deleted {} expired ID snapshot(s)", deleteCount)
        }

        private fun File.isStale(cutoff: Long): Boolean {
            val modified = lastModified()
            return isFile && modified > 0 && modified < cutoff
        }

        private fun File.deleteWithLogging(): Boolean =
            delete().also { deleted ->
                if (!deleted) logger.warn("Unable to delete expired ID snapshot: {}", this)
            }

        private inline fun File.withCleanupErrorHandling(block: () -> Boolean): Boolean =
            try {
                block()
            } catch (e: SecurityException) {
                logger.warn("Unable to access ID snapshot path: {}", this, e)
                false
            }

        private fun ensureDirectory(directory: File): File {
            if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
                throw IOException("Unable to create ID snapshot directory: $directory")
            }
            return directory
        }
    }
}
