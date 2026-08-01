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

package com.ichi2.audio

import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.R
import com.ichi2.anki.Reviewer
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.time.formatAsString
import com.ichi2.anki.multimedia.audio.AudioRecordingController
import com.ichi2.anki.multimedia.audio.AudioRecordingController.RecordingState
import com.ichi2.themes.Themes
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowMediaPlayer
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/** Tests [AudioRecordingController] */
@RunWith(AndroidJUnit4::class)
class AudioRecordingControllerAndroidTest : RobolectricTest() {
    private lateinit var layout: LinearLayout

    override fun setUp() {
        super.setUp()
        grantRecordAudioPermission()
    }

    @Test
    @Ignore("does not fail when expected under Robolectric")
    fun `Voice Playback handles onPause`() =
        withVoicePlayback {
            Timber.v("start recording")
            layout.findViewById<MaterialButton?>(R.id.action_start_recording)?.performClick()
            Timber.v("stop recording")
            layout.findViewById<MaterialButton?>(R.id.action_start_recording)?.performClick()
            Timber.v(" playback recording")
            layout.findViewById<MaterialButton?>(R.id.action_start_recording)?.performClick()
            onViewFocusChanged()
            Timber.v("playback recording again")
            layout.findViewById<MaterialButton?>(R.id.action_start_recording)?.performClick()
        }

    /** Applies [block] to a [AudioRecordingController] generated for the [Reviewer] */
    private fun withVoicePlayback(block: AudioRecordingController.() -> Unit) {
        ShadowMediaPlayer.setMediaInfoProvider { ShadowMediaPlayer.MediaInfo(200, 1) }
        val layout = LinearLayout(targetContext)
        Themes.setTheme(targetContext)
        this.layout = layout
        AudioRecordingController(targetContext, layout).apply {
            // this shouldn't be here
            AudioRecordingController.tempAudioPath = AudioRecordingController.generateTempAudioFile(targetContext)
            AudioRecordingController.setEditorStatus(inEditField = false)
            createUI(
                context = targetContext,
                layout = layout,
                initialState = RecordingState.ImmediatePlayback.CLEARED,
                controllerLayout = R.layout.activity_audio_recording_reviewer,
            )
            block()
        }
    }
}

/** Tests [AudioRecordingController] */
class AudioRecordingControllerTest {
    @Test
    fun `original value is correctly formatted`() {
        // given that DEFAULT_TIME is a constant, we can't initialise it directly
        assertThat(AudioRecordingController.DEFAULT_TIME, equalTo(0.milliseconds.formatAsString()))
    }
}
