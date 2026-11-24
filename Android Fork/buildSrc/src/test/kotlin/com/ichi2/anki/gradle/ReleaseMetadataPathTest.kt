// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ReleaseMetadataPathTest {
    @Test
    fun `app build file stays at the path assumed by the publishing script`() {
        assertTrue(
            File("../AnkiDroid/build.gradle.kts").isFile,
            "Update .github/check_alpha_release.js if the AnkiDroid module is moved.",
        )
    }
}
