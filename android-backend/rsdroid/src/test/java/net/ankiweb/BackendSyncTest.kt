package net.ankiweb

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackendSyncTest {
    @Test
    fun verifyMediaFileSizeSync() {
        val file = File("../anki/rslib/src/sync/media/mod.rs")

        assertTrue(
            "File does not exist at ${file.absolutePath}",
            file.exists(),
        )

        val content = file.readText()

        val expected_MAX_MEDIA_FILENAME_LENGTH = "pub static MAX_MEDIA_FILENAME_LENGTH: usize = 120"

        val expected_MAX_MEDIA_FILENAME_LENGTH_SERVER = "pub const MAX_MEDIA_FILENAME_LENGTH_SERVER: usize = 255"

        val expected_MAX_INDIVIDUAL_MEDIA_FILE_SIZE = "pub static MAX_INDIVIDUAL_MEDIA_FILE_SIZE: usize = 100 * 1024 * 1024"

        assertTrue(
            "MAX_MEDIA_FILENAME_LENGTH in Backend.kt is out of sync with anki",
            content.contains(expected_MAX_MEDIA_FILENAME_LENGTH),
        )

        assertTrue(
            "MAX_MEDIA_FILENAME_LENGTH_SERVER in Backend.kt is out of sync with anki",
            content.contains(expected_MAX_MEDIA_FILENAME_LENGTH_SERVER),
        )

        assertTrue(
            "MAX_INDIVIDUAL_MEDIA_FILE_SIZE in Backend.kt is out of sync with anki",
            content.contains(expected_MAX_INDIVIDUAL_MEDIA_FILE_SIZE),
        )
    }
}
