// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import com.ichi2.anki.multiprofile.ProfileManager
import org.junit.After
import org.junit.Test

class ProfileLoadFailureScreenshotTest : ScreenshotTest() {
    @After
    fun resetProfileFailure() {
        ProfileManager.resetForTesting()
    }

    @Test
    fun `profile load failure dialog`() {
        failToOpenProfile()

        withDeckPicker(deckCount = 0) {
            captureScreen("profile_load_failure_dialog")
        }
    }

    @Test
    fun `profile load failure dialog with long names`() {
        failToOpenProfile(
            profileName = "Japanese vocabulary for the JLPT N2 exam in 2027",
            defaultProfileName = "Medical school flashcards, second year",
        )

        withDeckPicker(deckCount = 0) {
            captureScreen("profile_load_failure_dialog_long_names")
        }
    }
}
