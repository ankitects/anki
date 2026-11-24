// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.Manifest.permission.RECORD_AUDIO
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.os.Parcel
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.descendants
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.fields.TextField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.anki.recorder.MediaRecorderStub
import com.ichi2.testutils.AnkiFragmentScenario
import com.ichi2.testutils.grantPermissions
import com.ichi2.testutils.launchFragmentInContainer
import com.ichi2.testutils.withFragment
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.lessThan
import org.hamcrest.Matchers.nullValue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import java.io.File
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class MultimediaActivityTest : RobolectricTest() {
    private val mediaRecorderStub = MediaRecorderStub()

    @Before
    fun installMediaRecorder() {
        mediaRecorderStub.install()
    }

    @After
    fun uninstallMediaRecorder() {
        mediaRecorderStub.uninstall()
    }

    @Test
    fun `fragment loads and releases its arguments independently of the host`() {
        val args = largeNoteIntent().extras!!.parcelCopy()
        val argsFile = args.getSerializableCompat<File>(MultimediaActivity.EXTRA_FRAGMENT_ARGS)!!

        withAudioRecordingFragment(args) { scenario ->
            scenario.withFragment { assertNotePreview(this) }
            scenario.recreate()
            scenario.withFragment { assertNotePreview(this) }
            scenario.moveToState(Lifecycle.State.DESTROYED)
        }

        assertThat(argsFile.exists(), equalTo(false))
    }

    @Test
    fun `older fragments can be recreated after a newer launch`() {
        val args = largeNoteIntent().extras!!.parcelCopy()
        val argsFile = args.getSerializableCompat<File>(MultimediaActivity.EXTRA_FRAGMENT_ARGS)!!
        lateinit var newArgs: Bundle

        withAudioRecordingFragment(args) { scenario ->
            newArgs = largeNoteIntent().extras!!.parcelCopy()
            assertThat(argsFile.exists(), equalTo(true))
            scenario.recreate()
            scenario.withFragment { assertNotePreview(this) }
        }
        withAudioRecordingFragment(newArgs) { scenario ->
            scenario.withFragment { assertNotePreview(this) }
        }
    }

    @Test
    fun `closing an older fragment preserves the arguments of a newer launch`() {
        lateinit var newArgs: Bundle
        withAudioRecordingFragment(largeNoteIntent().extras!!) { scenario ->
            newArgs = largeNoteIntent().extras!!.parcelCopy()
            scenario.moveToState(Lifecycle.State.DESTROYED)
        }

        withAudioRecordingFragment(newArgs) { scenario ->
            scenario.withFragment { assertNotePreview(this) }
        }
    }

    @Test
    fun `launching multimedia with a large note keeps the intent small`() {
        assertThat(largeNoteIntent().extras!!.parcelSize(), lessThan(64 * 1024))
    }

    @Test
    fun `saving multimedia with a large note keeps the activity state small`() {
        grantPermissions(RECORD_AUDIO)
        val controller =
            startActivityControllerNormallyOpenCollectionWithIntent(
                MultimediaActivity::class.java,
                largeNoteIntent(),
            )
        val savedState = Bundle()
        controller.pause().stop().saveInstanceState(savedState)

        assertThat(savedState.parcelSize(), lessThan(64 * 1024))
    }

    @Test
    fun `missing cached arguments show an error instead of restoring the editor`() {
        val intent = largeNoteIntent()
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(MultimediaActivity::class.java, intent)
        val permissionRequest = shadowOf(controller.get()).lastRequestedPermission
        val savedState = Bundle()
        controller
            .pause()
            .stop()
            .saveInstanceState(savedState)
            .destroy()
        intent.extras!!.getSerializableCompat<File>(MultimediaActivity.EXTRA_FRAGMENT_ARGS)!!.delete()

        val activity = restoreActivity(intent, savedState).get()

        assertThat(activity.supportFragmentManager.findFragmentById(R.id.fragment_container)!!.view, nullValue())
        activity.onRequestPermissionsResult(permissionRequest.requestCode, arrayOf(RECORD_AUDIO), intArrayOf(PERMISSION_GRANTED))
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        advanceRobolectricLooper()
        assertThat(activity.isFinishing, equalTo(true))
    }

    @Test
    fun `cancelling the missing arguments dialog closes the empty activity`() {
        val intent = largeNoteIntent()
        intent.extras!!.getSerializableCompat<File>(MultimediaActivity.EXTRA_FRAGMENT_ARGS)!!.delete()
        val activity = startActivityControllerNormallyOpenCollectionWithIntent(MultimediaActivity::class.java, intent).get()
        assertThat(activity.supportFragmentManager.findFragmentById(R.id.fragment_container)!!.view, nullValue())

        ShadowDialog.getLatestDialog().cancel()
        advanceRobolectricLooper()

        assertThat(activity.isFinishing, equalTo(true))
    }

    @Test
    fun `missing arguments allow the audio video fragment to close without a player`() {
        val args = largeNoteIntent().extras!!.parcelCopy()
        args.getSerializableCompat<File>(MultimediaActivity.EXTRA_FRAGMENT_ARGS)!!.delete()
        args.putSerializable(MultimediaActivity.EXTRA_MEDIA_OPTIONS, AudioVideoFragment.MediaOption.AUDIO_CLIP)

        launchFragmentInContainer<AudioVideoFragment>(args).use { scenario ->
            scenario.withFragment { assertThat(view, nullValue()) }
            scenario.moveToState(Lifecycle.State.DESTROYED)
        }
    }

    @Test
    fun `note context and result survive recreation and process death`() {
        grantPermissions(RECORD_AUDIO)
        val intent = largeNoteIntent().apply { replaceExtras(extras!!.parcelCopy()) }
        val argsFile = intent.extras!!.getSerializableCompat<File>(MultimediaActivity.EXTRA_FRAGMENT_ARGS)!!
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(MultimediaActivity::class.java, intent)

        controller.recreate()
        advanceRobolectricLooper()
        assertThat(argsFile.exists(), equalTo(true))
        assertNotePreview(controller.get())

        val savedState = Bundle()
        controller
            .pause()
            .stop()
            .saveInstanceState(savedState)
            .destroy()
        assertThat(argsFile.exists(), equalTo(true))
        val restored = restoreActivity(intent, savedState)
        val activity = restored.get()
        assertNotePreview(activity)

        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as AudioRecordingFragment
        val recordButton = fragment.requireView().findViewById<View>(R.id.action_start_recording)
        val time = SystemClock.uptimeMillis()
        recordButton.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 0f, 0f, 0))
        fragment.requireView().findViewById<View>(R.id.action_save_recording).performClick()
        advanceRobolectricLooper()
        fragment.binding.actionDone.performClick()
        val recording = mediaRecorderStub.outputFiles.single()

        val shadow = shadowOf(activity)
        val result = assertIs<MultimediaResult.Success>(MultimediaResultContract().parseResult(shadow.resultCode, shadow.resultIntent))
        assertThat(result.fieldIndex, equalTo(1))
        assertIs<AudioRecordingField>(result.field)
        assertThat(result.field.mediaFile, equalTo(recording))
        assertThat(result.field.hasTemporaryMedia, equalTo(true))
        restored.pause().stop().destroy()
        assertThat("finished activities release the note data", argsFile.exists(), equalTo(false))
    }

    private fun withAudioRecordingFragment(
        args: Bundle,
        block: (AnkiFragmentScenario<AudioRecordingFragment>) -> Unit,
    ) {
        grantPermissions(RECORD_AUDIO)
        launchFragmentInContainer<AudioRecordingFragment>(args).use(block)
    }

    private fun restoreActivity(
        intent: Intent,
        savedState: Bundle,
    ): ActivityController<MultimediaActivity> {
        val restoredState = savedState.parcelCopy()
        return Robolectric
            .buildActivity(MultimediaActivity::class.java, Intent(intent).apply { replaceExtras(intent.extras!!.parcelCopy()) })
            .create(restoredState)
            .start()
            .restoreInstanceState(restoredState)
            .postCreate(restoredState)
            .resume()
            .visible()
            .also {
                saveControllerForCleanup(it)
                advanceRobolectricLooper()
            }
    }

    private fun assertNotePreview(activity: MultimediaActivity) {
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as AudioRecordingFragment
        assertNotePreview(fragment)
    }

    private fun assertNotePreview(fragment: AudioRecordingFragment) {
        val texts =
            fragment.binding.audioRecorderLayout.descendants
                .filterIsInstance<TextView>()
                .map { it.text.toString() }
                .toList()
        assertThat(texts.contains("front".repeat(30_000)), equalTo(true))
        assertThat(texts.contains("back"), equalTo(true))
    }

    private fun largeNoteIntent(): Intent {
        val note =
            MultimediaEditableNote().apply {
                setNumFields(2)
                setField(0, TextField().apply { text = "front".repeat(30_000) })
                setField(1, TextField().apply { text = "back" })
                freezeInitialFieldValues()
            }
        return AudioRecordingFragment.getIntent(
            targetContext,
            MultimediaActivityExtra(index = 1, field = AudioRecordingField(), note = note),
        )
    }

    private fun Bundle.parcelSize(): Int {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(this)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    private fun Bundle.parcelCopy(): Bundle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(this)
            parcel.setDataPosition(0)
            parcel.readBundle(MultimediaActivity::class.java.classLoader)!!
        } finally {
            parcel.recycle()
        }
    }
}
