// SPDX-License-Identifier: GPL-3.0-or-later

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
