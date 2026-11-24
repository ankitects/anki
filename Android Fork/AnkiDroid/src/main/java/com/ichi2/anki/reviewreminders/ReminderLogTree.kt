// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.ellipsize
import com.ichi2.anki.common.utils.ext.indexOfNewlineAtOrAfter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Name used by the prefix which marks a [Timber] message for persistence by [ReminderLogTree].
 */
private const val REMINDER_TAG = "Reminders"

/**
 * A [Timber.Tree] which persists review reminder logs to a file.
 * Logs are routed here by prefixing the [Timber] message with [reminderLogPrefix]; other messages are ignored.
 *
 * Many review reminder events like "notification fired" and "notification attempted" are difficult to test
 * (may need to wait a whole day to test again) or fire at non-deterministic times due to OS intervention.
 * Bugs such as missing notifications may be noticed by developers long after logs have been rotated out of memory.
 * Users may find describing issues on forums difficult beyond "I didn't get a notification".
 * This small set of logs is persisted to a file and is intended to aid in bug reporting and diagnosis.
 *
 * @param context Used to locate the log file.
 */
@SuppressLint("LogNotTimber")
class ReminderLogTree(
    context: Context,
) : Timber.Tree() {
    /**
     * We store the log file in [Context.getNoBackupFilesDir], which requires no permission on any build flavor, is
     * never reclaimed by the OS when storage is low, and is excluded from device backups: the log describes the
     * behavior of alarms on *this* device, so entries restored from another device would be misleading.
     */
    @VisibleForTesting
    val logFile = File(context.noBackupFilesDir, LOG_FILE_NAME)

    private val scope: CoroutineScope = applicationScope

    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    /**
     * Overridden in tests to simulate a persistently failing rename.
     */
    @VisibleForTesting
    var renameTemporaryFile: (from: File, to: File) -> Boolean = { from, to -> from.renameTo(to) }

    override fun isLoggable(
        tag: String?,
        priority: Int,
    ): Boolean = priority >= Log.INFO

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        val header = HEADER_REGEX.matchAt(message, 0) ?: return
        // Take timestamp as soon as possible to avoid logging a time that is later than the actual event
        val timestamp = TimeManager.time.currentDate
        scope.launch(dispatcher) {
            val line = formatLine(timestamp, priority, header, message)
            append(line)
        }
    }

    /**
     * The contents of the log file, oldest entry first, or an empty string if nothing has been logged yet.
     */
    suspend fun readLog(): String? =
        withContext(dispatcher) {
            try {
                if (logFile.exists()) logFile.readText() else null
            } catch (e: IOException) {
                Log.w(REMINDER_TAG, "Failed to read review reminder log", e)
                null
            }
        }

    /**
     * Formats a single log entry line. Examples:
     *
     * ```
     * Timber.i("${reminderLogPrefix(ReviewReminderId(7))} Successfully scheduled notifications")
     * // 2026-09-05 08:30:00.123 I #7 Successfully scheduled notifications
     *
     * Timber.i("${reminderLogPrefix()} scheduleAllEnabledReviewReminderNotifications")
     * // 2026-09-05 08:30:00.123 I #-- scheduleAllEnabledReviewReminderNotifications
     * ```
     *
     * @param timestamp The time of the log entry.
     * @param priority The log level of the entry, as passed to [log].
     * @param header The [reminderLogPrefix] header matched at the start of [message]. Its captured group holds the
     * reminder ID which the entry is about, and is empty if the entry is not about a specific reminder.
     * @param message The log message, still carrying its [header], which is stripped off. Newlines in it are escaped
     * so that one entry always occupies exactly one line, and overly long messages are truncated to [MAX_LINE_LENGTH].
     * @return A single line of text to append to the log file (timestamp, level character, reminder ID and message),
     * with a trailing newline. The reminder ID is replaced with [MISSING_ID] if the entry is not about a specific reminder.
     */
    private fun formatLine(
        timestamp: Date,
        priority: Int,
        header: MatchResult,
        message: String,
    ): String {
        val id = header.groupValues[1].ifEmpty { MISSING_ID }
        val escapedMessage = message.substring(header.value.length).replace("\n", "\\n")
        val truncatedMessage = escapedMessage.ellipsize(MAX_LINE_LENGTH)
        val time = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.US).format(timestamp)
        return "$time ${levelChar(priority)} #$id $truncatedMessage\n"
    }

    private fun levelChar(priority: Int): Char =
        when (priority) {
            Log.INFO -> 'I'
            Log.WARN -> 'W'
            Log.ERROR, Log.ASSERT -> 'E'
            else -> 'X'
        }

    /**
     * Appends [line] to the log file, truncating the file first if it has grown too large.
     */
    private fun append(line: String) {
        try {
            trimIfNeeded()
            FileOutputStream(logFile, true).use { it.write(line.toByteArray()) }
        } catch (e: IOException) {
            Log.w(REMINDER_TAG, "Failed to write to review reminder log", e)
        }
    }

    /**
     * Discards the oldest entries once the file exceeds [TRIM_TRIGGER_BYTES], leaving [TRIM_TARGET_BYTES].
     * Attempts to stage the trim in a temporary file and moves it into place atomically. If that fails,
     * falls back to an in-place slow truncation.
     */
    private fun trimIfNeeded() {
        if (logFile.length() < TRIM_TRIGGER_BYTES) return
        val bytes = logFile.readBytes()
        val newlineIndex = bytes.indexOfNewlineAtOrAfter(bytes.size - TRIM_TARGET_BYTES)
        val start = newlineIndex?.let { it + 1 } ?: bytes.size
        val trimmedBytes = bytes.copyOfRange(start, bytes.size)
        val temporaryFile = File(logFile.parentFile, "$LOG_FILE_NAME.tmp")
        temporaryFile.writeBytes(trimmedBytes)

        if (renameTemporaryFile(temporaryFile, logFile)) return
        temporaryFile.delete()
        Log.w(REMINDER_TAG, "Failed to atomically truncate review reminder log; rewriting it in place")
        logFile.writeBytes(trimmedBytes)
    }

    companion object {
        /**
         * Matches the [reminderLogPrefix] header, capturing the reminder id if the header carries one.
         */
        private val HEADER_REGEX = Regex("""\[$REMINDER_TAG(?::(\d+))?] ?""")

        /**
         * Stands in for the reminder id of an entry which is not about one specific reminder.
         */
        private const val MISSING_ID = "--"

        /**
         * Name of the log file written by [ReminderLogTree].
         */
        private const val LOG_FILE_NAME = "reminders.log"

        /**
         * The log file is truncated back down to this size once it exceeds [TRIM_TRIGGER_BYTES].
         */
        @VisibleForTesting
        const val TRIM_TARGET_BYTES = 24 * 1024

        /**
         * Size at which the log file is truncated back down to [TRIM_TARGET_BYTES].
         */
        @VisibleForTesting
        const val TRIM_TRIGGER_BYTES = TRIM_TARGET_BYTES * 3 / 2

        /**
         * Maximum length of a single line in the log file. Just in case really long exceptions are sent to the log file.
         */
        @VisibleForTesting
        const val MAX_LINE_LENGTH = 2 * 1024

        /**
         * Timestamp format for entries in the log file.
         */
        private const val TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS"

        /**
         * Gets the contents of the review reminder log from Timber.
         */
        suspend fun readReminderLog(): String =
            Timber
                .forest()
                .filterIsInstance<ReminderLogTree>()
                .firstOrNull()
                ?.readLog() ?: ""
    }
}

/**
 * The header to place at the start of a [Timber] message to have that log persisted by
 * [ReminderLogTree]. Only `i`, `w` and `e` are persisted; `d` and `v` are dropped.
 *
 * The header rides in the message rather than in the tag so that the tag keeps its default
 * meaning: development build logcat still reports the class the log came from, and is not flooded with one distinct tag
 * per reminder id. [ReminderLogTree] strips the header off before writing the entry to its file.
 *
 * Usage examples:
 * ```
 * Timber.i("${reminderLogPrefix(reminder.id)} Successfully scheduled notifications")
 * Timber.i("${reminderLogPrefix()} scheduleAllEnabledReviewReminderNotifications")
 * ```
 *
 * Output examples:
 * ```
 * reminderLogPrefix(ReviewReminderId(7)) // [Reminders:7]
 * reminderLogPrefix() // [Reminders]
 * ```
 *
 * @param id The reminder this log is about, or `null` if it is not about a specific reminder.
 */
fun reminderLogPrefix(id: ReviewReminderId? = null): String = if (id == null) "[$REMINDER_TAG]" else "[$REMINDER_TAG:${id.value}]"
