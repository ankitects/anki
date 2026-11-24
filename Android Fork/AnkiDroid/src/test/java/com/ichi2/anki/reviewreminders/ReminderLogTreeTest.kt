// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.ichi2.anki.reviewreminders.ReminderLogTree.Companion.MAX_LINE_LENGTH
import com.ichi2.anki.reviewreminders.ReminderLogTree.Companion.TRIM_TARGET_BYTES
import com.ichi2.anki.reviewreminders.ReminderLogTree.Companion.TRIM_TRIGGER_BYTES
import com.ichi2.testutils.rules.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.lessThan
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import timber.log.Timber
import java.io.File

class ReminderLogTreeTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var tree: ReminderLogTree
    private val temporaryFile: File
        get() = File(tree.logFile.parentFile, "${tree.logFile.name}.tmp")

    @Before
    @SuppressLint("LogNotTimber") // Mock the Android logging used internally by ReminderLogTree.
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any<Throwable>()) } returns 0
        val context =
            mockk<Context> {
                every { noBackupFilesDir } returns tempFolder.root
            }
        // Own the tree: application initialization may have planted zero or multiple reminder trees.
        Timber.forest().filterIsInstance<ReminderLogTree>().forEach(Timber::uproot)
        tree = ReminderLogTree(context).also { Timber.plant(it) }
        tree.logFile.delete()
        temporaryFile.delete()
    }

    @After
    fun tearDown() {
        try {
            Timber.uproot(tree)
            awaitLogWrites()
        } finally {
            unmockkStatic(Log::class)
        }
    }

    @Test
    fun `reminderLogPrefix identifies the reminder a log is about`() {
        assertThat("a log about no reminder in particular", reminderLogPrefix(), equalTo("[Reminders]"))
        assertThat("a log about one reminder", reminderLogPrefix(ReviewReminderId(7)), equalTo("[Reminders:7]"))
    }

    @Test
    fun `persists entries at info and above`() {
        Timber.i("${reminderLogPrefix()} info")
        Timber.w("${reminderLogPrefix()} warning")
        Timber.e("${reminderLogPrefix()} error")
        Timber.wtf("${reminderLogPrefix()} assert")
        awaitLogWrites()

        assertThat(
            "every level at or above info should be persisted, with its own level character",
            entries(),
            equalTo("I #-- info\nW #-- warning\nE #-- error\nE #-- assert\n"),
        )
    }

    @Test
    fun `discards entries below info`() {
        Timber.d("${reminderLogPrefix()} debug")
        Timber.v("${reminderLogPrefix()} verbose")
        awaitLogWrites()

        assertThat("debug and verbose should not reach the log file", tree.logFile.exists(), equalTo(false))
    }

    @Test
    fun `records the reminder id carried by the header`() {
        Timber.i("${reminderLogPrefix(ReviewReminderId(7))} scheduled")
        awaitLogWrites()

        assertThat("the id from the header should be recorded", entries(), equalTo("I #7 scheduled\n"))
    }

    @Test
    fun `does not require a space between the header and the message`() {
        Timber.i("${reminderLogPrefix(ReviewReminderId(7))}scheduled")
        awaitLogWrites()

        assertThat("the header alone should mark the log", entries(), equalTo("I #7 scheduled\n"))
    }

    @Test
    fun `logs which are not marked as reminders are ignored`() {
        Timber.i("no header at all")
        awaitLogWrites()

        assertThat("nothing should have been logged", tree.logFile.exists(), equalTo(false))
    }

    @Test
    fun `logs whose message merely resembles a reminder header are ignored`() {
        Timber.i("[RemindersFoo] a class which happens to start with the same word")
        Timber.i("[Something] an unrelated bracketed prefix")
        Timber.i("[reminders] the wrong case")
        Timber.i("Reminders: no brackets")
        Timber.i("prefixed later on: ${reminderLogPrefix()} not at the start")
        awaitLogWrites()

        assertThat("only an exact header should be persisted", tree.logFile.exists(), equalTo(false))
    }

    @Test
    fun `stamps each entry with a timestamp`() {
        Timber.i("${reminderLogPrefix()} stamped")
        awaitLogWrites()

        val matchesTimestamp = TIMESTAMP_REGEX.containsMatchIn(tree.logFile.readText())
        assertThat("the entry should begin with a timestamp", matchesTimestamp, equalTo(true))
    }

    @Test
    fun `appends entries oldest first`() {
        Timber.i("${reminderLogPrefix()} first")
        Timber.i("${reminderLogPrefix()} second")
        awaitLogWrites()

        assertThat("entries should be appended in the order they were logged", entries(), equalTo("I #-- first\nI #-- second\n"))
    }

    @Test
    fun `escapes newlines so that an entry always occupies one line`() {
        Timber.i("${reminderLogPrefix()} first line\nsecond line")
        awaitLogWrites()

        assertThat("the newline should have been escaped", entries(), equalTo("I #-- first line\\nsecond line\n"))
    }

    @Test
    fun `truncates overly long messages`() {
        // +1 for the ellipsis, +1 for the newline, +1 to trigger the truncation
        val longMessage = "a".repeat(MAX_LINE_LENGTH + PREFIX_BYTES + 1 + 1 + 1)
        Timber.i("${reminderLogPrefix()} $longMessage")
        awaitLogWrites()

        val text = tree.logFile.readText()
        assertThat("the message should have been truncated", text.length, lessThan(longMessage.length))
        assertThat("the truncation should be marked", text, endsWith("…\n"))
        assertThat("the entry should still occupy exactly one line", text.trimEnd('\n').contains('\n'), equalTo(false))
    }

    @Test
    fun `readLog returns null before anything has been logged`() {
        assertThat("there is no log file yet", runBlocking { tree.readLog() }, nullValue())
    }

    @Test
    fun `readLog returns the contents of the log file`() {
        log(NEWEST)
        awaitLogWrites()

        assertThat("readLog should return what was written", runBlocking { tree.readLog() }, equalTo(tree.logFile.readText()))
    }

    @Test
    fun `readLog failing to read should not crash the app`() {
        tree.logFile.mkdirs() // The OS refuses to open a directory for reading

        assertThat("the failed read should be reported as no log at all", runBlocking { tree.readLog() }, nullValue())
    }

    @Test
    fun `readReminderLog returns an empty string before anything has been logged`() {
        val log = runBlocking { ReminderLogTree.readReminderLog() }

        assertThat("an absent log file should read as empty", log, equalTo(""))
    }

    @Test
    fun `readReminderLog reads the planted tree's log file`() {
        log(NEWEST)
        awaitLogWrites()

        val log = runBlocking { ReminderLogTree.readReminderLog() }

        assertThat("the planted tree's log should be returned", log, equalTo(tree.logFile.readText()))
    }

    @Test
    fun `trims by rewriting the log file in place when the atomic move fails`() {
        fillPastTrimTrigger()
        tree.renameTemporaryFile = { _, _ -> false }

        log(NEWEST)
        awaitLogWrites()

        val text = tree.logFile.readText()
        assertThat("log file should have been trimmed", tree.logFile.length(), lessThan(TRIM_TRIGGER_BYTES.toLong()))
        assertThat("the newest entry should have been appended", text, endsWith("${message(NEWEST)}\n"))
        assertThat("trimming should discard the oldest entries", text, not(startsWith(entry(0))))
        assertThat("trimming should cut at an entry boundary", text, startsWith("entry "))
        assertThat("the temporary file should not be left behind", temporaryFile.exists(), equalTo(false))
    }

    @Test
    fun `log file stays bounded when the atomic move keeps failing`() {
        fillPastTrimTrigger()
        tree.renameTemporaryFile = { _, _ -> false }

        repeat(TRIM_TARGET_BYTES / ENTRY_BYTES) { log(NEWEST) }
        awaitLogWrites()

        assertThat("repeated failures should not grow the log", tree.logFile.length(), lessThan(TRIM_TRIGGER_BYTES.toLong()))
    }

    @Test
    fun `trims via the atomic move when it succeeds`() {
        fillPastTrimTrigger()

        log(NEWEST)
        awaitLogWrites()

        val text = tree.logFile.readText()
        assertThat("log file should have been trimmed", tree.logFile.length(), lessThan(TRIM_TRIGGER_BYTES.toLong()))
        assertThat("the newest entry should have been appended", text, endsWith("${message(NEWEST)}\n"))
        assertThat("trimming should cut at an entry boundary", text, startsWith("entry "))
        assertThat("the temporary file should not be left behind", temporaryFile.exists(), equalTo(false))
    }

    @Test
    fun `discards the whole log file when it holds no entry boundary to cut at`() {
        tree.logFile.writeText("no newlines anywhere in here".padEnd(TRIM_TRIGGER_BYTES))

        log(NEWEST)
        awaitLogWrites()

        val text = tree.logFile.readText()
        assertThat("everything before the newest entry should have been discarded", text.length, equalTo(ENTRY_BYTES))
        assertThat("the newest entry should have been appended", text, endsWith("${message(NEWEST)}\n"))
    }

    @Test
    fun `does not trim a log file below the trim trigger`() {
        tree.logFile.writeText(entry(0))

        log(NEWEST)
        awaitLogWrites()

        val text = tree.logFile.readText()
        assertThat("nothing should have been discarded", text, startsWith(entry(0)))
        assertThat("the newest entry should have been appended", text, endsWith("${message(NEWEST)}\n"))
        assertThat("only the newest entry should have been added", text.length, equalTo(2 * ENTRY_BYTES))
    }

    @Test
    fun `truncation constants are correctly ordered`() {
        assertThat("the trim trigger should be larger than the trim target", TRIM_TRIGGER_BYTES, greaterThan(TRIM_TARGET_BYTES))
        assertThat("the trim target should be larger than the max line length", TRIM_TARGET_BYTES, greaterThan(MAX_LINE_LENGTH))
    }

    /**
     * The message which, once [ReminderLogTree] has prefixed it with a timestamp and a reminder id,
     * produces a log entry of exactly [ENTRY_BYTES] bytes, identifiable by [index].
     */
    private fun message(index: Int): String = "entry $index".padEnd(ENTRY_BYTES - PREFIX_BYTES - 1)

    /**
     * A log entry of exactly [ENTRY_BYTES] bytes, identifiable by [index],
     * written directly to the log file to stand in for entries logged earlier.
     */
    private fun entry(index: Int): String = "entry $index".padEnd(ENTRY_BYTES - 1) + "\n"

    /**
     * Logs [message] via [Timber], which [ReminderLogTree] appends to the log file.
     */
    private fun log(index: Int) = Timber.i("${reminderLogPrefix()} ${message(index)}")

    /**
     * The log file with the timestamp stripped off the front of every entry, so that entries can be
     * compared exactly. That a timestamp is present at all is asserted by its own test.
     */
    private fun entries(): String = tree.logFile.readText().replace(TIMESTAMP_REGEX, "")

    /**
     * Waits for the entries logged so far to reach the log file: reads and writes share a
     * single-threaded dispatcher, so this read cannot run before the writes queued before it.
     */
    private fun awaitLogWrites() = runBlocking { tree.readLog() }

    /**
     * Fills the log file past [TRIM_TRIGGER_BYTES] so that the next entry logged must trim it.
     */
    private fun fillPastTrimTrigger() {
        val entryCount = TRIM_TRIGGER_BYTES / ENTRY_BYTES + 1
        tree.logFile.writeText((0 until entryCount).joinToString(separator = "") { entry(it) })
        assertThat("test setup should exceed the trim trigger", tree.logFile.length() >= TRIM_TRIGGER_BYTES, equalTo(true))
    }

    companion object {
        /**
         * Size in bytes of each entry logged by [log], chosen so entry counts are easy to reason about.
         */
        private const val ENTRY_BYTES = 100

        /**
         * Size in bytes of the `2020-07-07 07:00:00.000 I #-- ` prefix which [ReminderLogTree] puts
         * in front of every message. Asserted indirectly by the entry sizes the tests expect.
         */
        private const val PREFIX_BYTES = 30

        /**
         * Index identifying the entry logged by the test, as opposed to the pre-existing ones.
         */
        private const val NEWEST = 999999

        /**
         * The `yyyy-MM-dd HH:mm:ss.SSS ` timestamp which begins every entry.
         */
        private val TIMESTAMP_REGEX = Regex("""(?m)^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} """)
    }
}
