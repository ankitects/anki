// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.Manifest.permission.RECORD_AUDIO
import android.app.Activity
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.annotation.IdRes
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multimedia.audio.AudioRecordingController
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.fields.TextField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.anki.recorder.MediaRecorderStub
import com.ichi2.testutils.grantPermissions
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertIs

/** Tests for [AudioRecordingFragment]. */
@RunWith(AndroidJUnit4::class)
class AudioRecordingFragmentTest : RobolectricTest() {
    private val mediaRecorderStub = MediaRecorderStub()

    @Before
    fun installRecorder() {
        mediaRecorderStub.install()
    }

    @After
    fun resetRecorder() {
        mediaRecorderStub.uninstall()
        AudioRecordingController.setEditorStatus(true)
        AudioRecordingController.tempAudioPath = null
    }

    @Test
    fun `done button is disabled when no media path is set`() =
        withAudioRecordingFragment { fragment ->
            assertThat("Done button should be disabled initially", fragment.binding.actionDone.isEnabled, equalTo(false))
        }

    @Test
    fun `done button is enabled once a recording is saved`() =
        withAudioRecordingFragment { fragment ->
            fragment.record()

            assertThat("Done button should be enabled after saving a recording", fragment.binding.actionDone.isEnabled, equalTo(true))
        }

    @Test
    fun `the recorder is added to audio_recorder_layout`() =
        withAudioRecordingFragment { fragment ->
            assertThat(
                "The recorder should inflate into audio_recorder_layout",
                fragment.binding.audioRecorderLayout.childCount,
                greaterThan(0),
            )
        }

    @Test
    fun `a recording can be added after the reviewer's recorder was used`() {
        AudioRecordingController.tempAudioPath = AudioRecordingController.generateTempAudioFile(targetContext)
        AudioRecordingController.setEditorStatus(false)
        val controller = startRecorder()
        val fragment = controller.fragment

        fragment.record()

        assertThat("Done is enabled after saving", fragment.binding.actionDone.isEnabled, equalTo(true))
        val result = controller.finishWithDone()
        val recording = result.field.mediaFile!!
        assertThat("the recording exists", recording.exists(), equalTo(true))
        assertThat("the recording is not empty", recording.length(), greaterThan(0L))
    }

    @Test
    fun `a recording that fails to stop cannot be added and shows an error`() {
        mediaRecorderStub.failStop = true
        val fragment = startRecorder().fragment

        fragment.record()

        assertThat("Done stays disabled", fragment.binding.actionDone.isEnabled, equalTo(false))
        assertThat(
            "error message",
            ShadowToast.getTextOfLatestToast(),
            equalTo(targetContext.getString(CommonString.multimedia_editor_audio_view_recording_failed)),
        )
    }

    @Test
    fun `a saved recording can still be played after the screen is recreated`() {
        val controller = startRecorder()
        controller.fragment.record()

        controller.recreate()
        advanceRobolectricLooper()

        val fragment = controller.fragment
        assertThat("the play controls are shown", fragment.isShown(R.id.play_buttons_layout), equalTo(true))
        assertThat("Done is enabled", fragment.binding.actionDone.isEnabled, equalTo(true))
    }

    @Test
    fun `discarding a saved recording deletes it`() {
        val fragment = startRecorder().fragment
        fragment.record()
        val recording = mediaRecorderStub.outputFiles.single()

        fragment.click(R.id.action_discard_recording)

        assertThat("the discarded recording is deleted", recording.exists(), equalTo(false))
    }

    @Test
    fun `a recording continues when the screen is recreated`() {
        val controller = startRecorder()
        controller.fragment.pressRecord()

        controller.recreate()
        advanceRobolectricLooper()

        verify(exactly = 0) { mediaRecorderStub.mediaRecorder.stop() }
        controller.fragment.click(R.id.action_save_recording)
        val result = controller.finishWithDone()
        val recording = mediaRecorderStub.outputFiles.single()
        assertThat("the recording started before recreation is added", result.field.mediaFile, equalTo(recording))
    }

    @Test
    fun `leaving the screen while recording keeps the recording`() {
        val controller = startRecorder()
        controller.fragment.pressRecord()

        controller.pause().stop()
        verify { mediaRecorderStub.mediaRecorder.stop() }
        controller.start().resume()
        advanceRobolectricLooper()

        val fragment = controller.fragment
        assertThat("the play controls are shown", fragment.isShown(R.id.play_buttons_layout), equalTo(true))
        assertThat("Done is enabled", fragment.binding.actionDone.isEnabled, equalTo(true))
    }

    @Test
    fun `the screen can turn off once leaving the screen ends a recording`() {
        val controller = startRecorder()
        controller.fragment.pressRecord()
        assertThat("the screen stays on while recording", controller.fragment.keepsScreenOn, equalTo(true))

        controller.pause().stop()

        assertThat("the screen can turn off", controller.fragment.keepsScreenOn, equalTo(false))
    }

    @Test
    fun `the screen can turn off while a recording is paused`() {
        val fragment = startRecorder().fragment
        fragment.pressRecord()

        fragment.pressRecord()

        verify { mediaRecorderStub.mediaRecorder.pause() }
        assertThat("the screen can turn off", fragment.keepsScreenOn, equalTo(false))
    }

    private fun withAudioRecordingFragment(block: (AudioRecordingFragment) -> Unit) {
        block(startRecorder().fragment)
    }

    private fun startRecorder(): ActivityController<MultimediaActivity> {
        grantPermissions(RECORD_AUDIO)
        val note =
            MultimediaEditableNote().apply {
                setNumFields(1)
                setField(0, TextField().apply { text = "Front of the card" })
                freezeInitialFieldValues()
            }
        val extra = MultimediaActivityExtra(index = 0, field = AudioRecordingField(), note = note)
        return startActivityControllerNormallyOpenCollectionWithIntent(
            MultimediaActivity::class.java,
            AudioRecordingFragment.getIntent(targetContext, extra),
        )
    }

    private val ActivityController<MultimediaActivity>.fragment: AudioRecordingFragment
        get() = get().supportFragmentManager.findFragmentById(R.id.fragment_container) as AudioRecordingFragment

    private fun ActivityController<MultimediaActivity>.finishWithDone(): MultimediaResult.Success {
        fragment.binding.actionDone.performClick()
        val shadow = shadowOf(get())
        assertThat("result code", shadow.resultCode, equalTo(Activity.RESULT_OK))
        return assertIs<MultimediaResult.Success>(MultimediaResultContract().parseResult(shadow.resultCode, shadow.resultIntent))
    }

    private fun AudioRecordingFragment.record() {
        pressRecord()
        click(R.id.action_save_recording)
    }

    private fun AudioRecordingFragment.pressRecord() {
        val button = requireView().findViewById<View>(R.id.action_start_recording)
        val time = SystemClock.uptimeMillis()
        button.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0f, 0f, 0))
        button.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_UP, 0f, 0f, 0))
        advanceRobolectricLooper()
    }

    private fun AudioRecordingFragment.click(
        @IdRes id: Int,
    ) {
        requireView().findViewById<View>(id).performClick()
        advanceRobolectricLooper()
    }

    private val AudioRecordingFragment.keepsScreenOn: Boolean
        get() = requireView().findViewById<View>(R.id.audioView).keepScreenOn

    private fun AudioRecordingFragment.isShown(
        @IdRes id: Int,
    ): Boolean = requireView().findViewById<View>(id).isVisible
}
