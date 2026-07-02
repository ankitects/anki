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

package com.ichi2.anki.browser

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

/**
 * Tests for [BrowserMultiColumnAdapter]
 */
class BrowserMultiColumnAdapterTest {
    companion object {
        const val EXPECTED_SOUND = "\uD83D\uDD09david.mp3\uD83D\uDD09"
        const val TTS = "\uD83D\uDCACTest\uD83D\uDCAC"
    }

    @Test
    fun `sound without filenames`() {
        val text = BrowserMultiColumnAdapter.removeSounds(EXPECTED_SOUND, showMediaFilenames = false)
        assertThat("sound filename stripped", text, equalTo(""))
    }

    @Test
    fun `tts not affected`() {
        val text = BrowserMultiColumnAdapter.removeSounds(TTS, showMediaFilenames = false)
        assertThat("unchanged", text, equalTo(TTS))
    }

    @Test
    fun `sound with filenames`() {
        val text = BrowserMultiColumnAdapter.removeSounds(EXPECTED_SOUND, showMediaFilenames = true)
        assertThat("unchanged", text, equalTo(EXPECTED_SOUND))
    }

    @Test
    fun `meta test`() {
        // ensure that Anki's output has not changed
        assertThat("sound", EXPECTED_SOUND, equalTo(CardBrowserViewModelTest.EXPECTED_SOUND))
        assertThat("tts", TTS, equalTo(CardBrowserViewModelTest.EXPECTED_TTS))
    }
}
