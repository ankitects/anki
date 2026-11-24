// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2011 Norbert Nagold <norbert.nagold@gmail.com>

package com.ichi2.anki

import android.text.format.DateFormat
import com.ichi2.anki.common.time.Time
import com.ichi2.anki.common.time.Time.Companion.utcOffset
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Utils
import com.ichi2.utils.FileUtil.getFreeDiskSpace
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

open class BackupManager {
    companion object {
        private const val MIN_FREE_SPACE = 10
        private const val BACKUP_SUFFIX = "backup"
        const val BROKEN_COLLECTIONS_SUFFIX = "broken"
        private val backupNameRegex: Regex by lazy {
            Regex("(?:collection|backup)-((\\d{4})-(\\d{2})-(\\d{2})-(\\d{2})[.-](\\d{2}))(?:\\.\\d{2})?.colpkg")
        }

        private val legacyDateFormat = SimpleDateFormat("yyyy-MM-dd-HH-mm")
        private val newDateFormat = SimpleDateFormat("yyyy-MM-dd-HH.mm")

        fun getBackupDirectory(ankidroidDir: File): File {
            val directory = File(ankidroidDir, BACKUP_SUFFIX)
            if (!directory.isDirectory && !directory.mkdirs()) {
                Timber.w("getBackupDirectory() mkdirs on %s failed", ankidroidDir)
            }
            return directory
        }

        fun getBackupDirectoryFromCollection(col: File): String = getBackupDirectory(col.parentFile!!).absolutePath

        private fun getBrokenDirectory(ankidroidDir: File): File {
            val directory = File(ankidroidDir, BROKEN_COLLECTIONS_SUFFIX)
            if (!directory.isDirectory && !directory.mkdirs()) {
                Timber.w("getBrokenDirectory() mkdirs on %s failed", ankidroidDir)
            }
            return directory
        }

        fun enoughDiscSpace(path: File): Boolean = getFreeDiscSpace(path) >= MIN_FREE_SPACE * 1024 * 1024

        /**
         * Get free disc space in bytes from path to Collection
         */
        fun getFreeDiscSpace(file: File): Long = getFreeDiskSpace(file, (MIN_FREE_SPACE * 1024 * 1024).toLong())

        /**
         * Run the sqlite3 command-line-tool (if it exists) on the collection to dump to a text file
         * and reload as a new database. Recently this command line tool isn't available on many devices
         *
         * @return whether the repair was successful
         */
        fun repairCollection(colFile: File): Boolean {
            val colPath = colFile.absolutePath
            val time = TimeManager.time
            Timber.i("BackupManager - RepairCollection")

            // repair file
            val execString = "sqlite3 $colPath .dump | sqlite3 $colPath.tmp"
            Timber.i("repairCollection - Execute: %s", execString)
            try {
                val cmd = arrayOf("/system/bin/sh", "-c", execString)
                val process = Runtime.getRuntime().exec(cmd)
                process.waitFor()
                if (!File("$colPath.tmp").exists()) {
                    Timber.e("repairCollection - dump to %s.tmp failed", colPath)
                    return false
                }
                if (!moveDatabaseToBrokenDirectory(colFile, false, time)) {
                    Timber.e("repairCollection - could not move corrupt file to broken directory")
                    return false
                }
                Timber.i("repairCollection - moved corrupt file to broken directory")
                val repairedFile = File("$colPath.tmp")
                return repairedFile.renameTo(colFile)
            } catch (e: IOException) {
                Timber.e(e, "repairCollection - error")
            } catch (e: InterruptedException) {
                Timber.e(e, "repairCollection - error")
            }
            return false
        }

        fun moveDatabaseToBrokenDirectory(
            colFile: File,
            moveConnectedFilesToo: Boolean,
            time: Time,
        ): Boolean {
            // move file
            val value: Date = time.genToday(utcOffset())
            var movedFilename =
                String.format(
                    Utils.ENGLISH_LOCALE,
                    colFile.name.replace(".anki2", "") +
                        "-corrupt-%tF.anki2",
                    value,
                )
            var movedFile = File(getBrokenDirectory(colFile.parentFile!!), movedFilename)
            var i = 1
            while (movedFile.exists()) {
                movedFile =
                    File(
                        getBrokenDirectory(colFile.parentFile!!),
                        movedFilename.replace(
                            ".anki2",
                            "-$i.anki2",
                        ),
                    )
                i++
            }
            movedFilename = movedFile.name
            if (!colFile.renameTo(movedFile)) {
                return false
            }
            if (moveConnectedFilesToo) {
                // move all connected files (like journals, directories...) too
                val colName = colFile.name
                val directory = File(colFile.parent!!)
                for (f in directory.listFiles()!!) {
                    if (f.name.startsWith(colName) &&
                        !f.renameTo(File(getBrokenDirectory(colFile.parentFile!!), f.name.replace(colName, movedFilename)))
                    ) {
                        return false
                    }
                }
            }
            return true
        }

        /**
         * Parses a string with backup naming pattern
         * @param fileName String with pattern "collection-yyyy-MM-dd-HH-mm.colpkg"
         * @return Its dateformat parsable string or null if it doesn't match naming pattern
         */
        fun getBackupTimeString(fileName: String): String? = backupNameRegex.matchEntire(fileName)?.groupValues?.get(1)

        /**
         * @return date in string if it matches backup naming pattern or null if not
         */
        fun parseBackupTimeString(timeString: String): Date? =
            try {
                legacyDateFormat.parse(timeString)
            } catch (_: ParseException) {
                try {
                    newDateFormat.parse(timeString)
                } catch (_: ParseException) {
                    null
                }
            }

        /**
         * @return date in fileName if it matches backup naming pattern or null if not
         */
        fun getBackupDate(fileName: String): Date? = getBackupTimeString(fileName)?.let { parseBackupTimeString(it) }

        /**
         * @return Array of files with names which matches the backup name pattern,
         * in order of creation.
         */
        fun getBackups(colFile: File): Array<File> {
            val files = getBackupDirectory(colFile.parentFile!!).listFiles() ?: arrayOf()
            val backups =
                files
                    .mapNotNull { file ->
                        getBackupTimeString(file.name)?.let { time ->
                            Pair(time, file)
                        }
                    }.sortedBy { it.first }
                    .map { it.second }
            return backups.toTypedArray()
        }

        /**
         * Delete backups as specified by [backupsToDelete],
         * throwing [IllegalArgumentException] if any of the files passed aren't actually backups.
         *
         * @return Whether all specified backups were successfully deleted.
         */
        @Throws(IllegalArgumentException::class)
        fun deleteBackups(
            collection: Collection,
            backupsToDelete: List<File>,
        ): Boolean {
            val allBackups = getBackups(collection.colDb)
            val invalidBackupsToDelete = backupsToDelete.toSet() - allBackups.toSet()

            if (invalidBackupsToDelete.isNotEmpty()) {
                throw IllegalArgumentException("Not backup files: $invalidBackupsToDelete")
            }

            return backupsToDelete.all { it.delete() }
        }

        fun removeDir(dir: File): Boolean {
            if (dir.isDirectory) {
                val files = dir.listFiles()
                for (aktFile in files!!) {
                    removeDir(aktFile)
                }
            }
            return dir.delete()
        }
    }
}

/**
 * Formatter that produces localized date & time strings for backups.
 * `getBestDateTimePattern` is used instead of `DateFormat.getInstance()` to produce dates
 * in format such as "02 Nov 2022" instead of "11/2/22" or "2/11/22", which can be confusing.
 */
class LocalizedUnambiguousBackupTimeFormatter {
    private val formatter =
        SimpleDateFormat(
            DateFormat.getBestDateTimePattern(Locale.getDefault(), "dd MMM yyyy HH:mm"),
        )

    fun getTimeOfBackupAsText(file: File): String {
        val backupDate = BackupManager.getBackupDate(file.name) ?: return file.name
        return formatter.format(backupDate)
    }
}
