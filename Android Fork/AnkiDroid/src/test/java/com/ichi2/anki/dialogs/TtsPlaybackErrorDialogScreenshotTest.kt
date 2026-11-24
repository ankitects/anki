// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.libanki.TTSTag
import org.junit.Test
import org.robolectric.Robolectric.buildActivity
import org.robolectric.RuntimeEnvironment

class TtsPlaybackErrorDialogScreenshotTest : ScreenshotTest() {
    @Test
    fun noEngines() = captureMissingEngineDialog("no_engines")

    @Test
    fun noEnginesLandscape() {
        RuntimeEnvironment.setQualifiers("+land")
        captureMissingEngineDialog("no_engines_landscape")
    }

    private fun captureMissingEngineDialog(name: String) {
        buildActivity(AnkiActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            // No TTS services are installed in Robolectric, as on a stock Fire Stick.
            val tag = TTSTag(fieldText = "Hello", lang = "en_US", voices = emptyList(), speed = null, otherArgs = emptyList())
            TtsPlaybackErrorDialog.ttsPlaybackErrorDialog(activity, activity.supportFragmentManager, tag)
            captureScreen(name)
        }
    }
}
