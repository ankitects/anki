// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.shareddeck

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.tests.InstrumentedTest
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Test of [DownloadFile] */
@RunWith(AndroidJUnit4::class)
class DownloadFileTest : InstrumentedTest() {
    @Test
    fun guessFileName_Issue17573() {
        // broken on API 35
        // https://github.com/ankidroid/Anki-Android/issues/17573
        // https://issuetracker.google.com/issues/382864232

        val downloadFile =
            DownloadFile(
                url = "https://ankiweb.net/svc/shared/download-deck/293204297?t=token",
                userAgent = "unused",
                contentDisposition = "attachment; filename=Goethe_Institute_A1_Wordlist.apkg",
                mimeType = "application/octet-stream",
            )

        assertThat(
            downloadFile.toFileName(extension = "apkg"),
            equalTo("Goethe_Institute_A1_Wordlist.apkg"),
        )
    }
}
