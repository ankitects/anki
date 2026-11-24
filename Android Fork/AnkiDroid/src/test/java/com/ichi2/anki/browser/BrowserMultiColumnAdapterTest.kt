// SPDX-License-Identifier: GPL-3.0-or-later

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
