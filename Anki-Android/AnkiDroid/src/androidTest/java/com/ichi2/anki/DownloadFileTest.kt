/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

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
