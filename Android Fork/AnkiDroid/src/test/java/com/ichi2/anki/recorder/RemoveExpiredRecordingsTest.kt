// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class RemoveExpiredRecordingsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val now = 1_800_000_000_000L

    @Test
    fun `cleanup removes only recordings older than seven days`() {
        val expired = createFile("ankidroid_audiorec1.3gp", 7.days + 1.seconds)
        val atCutoff = createFile("ankidroid_audiorec2.3gp", 7.days)
        val recent = createFile("ankidroid_audiorec3.3gp", 1.days)

        VoiceRecorder.removeExpiredRecordings(temporaryFolder.root, now)

        assertFalse(expired.exists(), "expired recording")
        assertTrue(atCutoff.exists(), "recording at the cutoff")
        assertTrue(recent.exists(), "recent recording")
    }

    @Test
    fun `cleanup keeps other files and folders`() {
        val unrelated = createFile("notes.txt", 8.days)
        val folder = temporaryFolder.newFolder("ankidroid_audiorec_folder.3gp")
        assertTrue(folder.setLastModified(now - 8.days.inWholeMilliseconds))

        VoiceRecorder.removeExpiredRecordings(temporaryFolder.root, now)

        assertTrue(unrelated.exists(), "unrelated file")
        assertTrue(folder.exists(), "folder")
    }

    @Test
    fun `cleanup ignores a missing recordings folder`() {
        VoiceRecorder.removeExpiredRecordings(File(temporaryFolder.root, "missing"), now)
    }

    private fun createFile(
        name: String,
        age: Duration,
    ): File =
        temporaryFolder.newFile(name).apply {
            assertTrue(setLastModified(now - age.inWholeMilliseconds))
        }
}
