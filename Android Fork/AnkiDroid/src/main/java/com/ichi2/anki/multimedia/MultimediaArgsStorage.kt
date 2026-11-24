// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.content.Context
import com.ichi2.anki.common.time.TimeManager
import timber.log.Timber
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.time.Duration.Companion.days

/** Stores note contents outside intents and saved state, which have a shared Binder size limit. */
internal class MultimediaArgsStorage private constructor(
    private val directory: File,
) {
    fun save(arguments: MultimediaActivityExtra): File =
        File.createTempFile(ARGS_FILE_PREFIX, ".tmp", directory).also { file ->
            try {
                ObjectOutputStream(file.outputStream().buffered()).use { it.writeObject(arguments) }
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        }

    fun read(file: File): MultimediaActivityExtra =
        ObjectInputStream(file.inputStream().buffered()).use { it.readObject() as MultimediaActivityExtra }

    /**
     * Reclaims arguments left behind when a screen could not run its cleanup, such as after process death.
     * Called at startup on an IO dispatcher. Saved screens older than [FILE_RETENTION] may no longer restore.
     */
    fun removeExpiredFiles(now: Long = TimeManager.time.intTimeMS()) {
        val cutoff = now - FILE_RETENTION.inWholeMilliseconds
        directory
            .listFiles { _, name -> name.startsWith(ARGS_FILE_PREFIX) && name.endsWith(".tmp") }
            ?.forEach { file ->
                val modified = file.lastModified()
                if (file.isFile && modified > 0 && modified < cutoff && !file.delete()) {
                    Timber.w("Unable to delete expired multimedia arguments: %s", file)
                }
            }
    }

    companion object {
        private const val ARGS_FILE_PREFIX = "multimedia-args"
        private val FILE_RETENTION = 7.days

        fun create(context: Context): MultimediaArgsStorage = MultimediaArgsStorage(context.cacheDir)
    }
}
