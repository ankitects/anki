/*
 *  Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>
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
package com.ichi2.anki.multimedia.audio

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.CheckResult
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.ichi2.anki.R
import com.ichi2.anki.Reviewer
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.time.formatAsString
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.compat.Compat
import com.ichi2.anki.compat.CompatHelper.Companion.compat
import com.ichi2.anki.compat.USAGE_TOUCH
import com.ichi2.anki.multimedia.AudioVideoFragment
import com.ichi2.anki.multimedia.MultimediaViewModel
import com.ichi2.anki.multimedia.audio.AudioRecordingController.RecordingState.AppendToRecording
import com.ichi2.anki.multimedia.audio.AudioRecordingController.RecordingState.ImmediatePlayback
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.recorder.AudioRecorder
import com.ichi2.anki.recorder.AudioTimer
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.OnHoldListener
import com.ichi2.anki.ui.setOnHoldListener
import com.ichi2.anki.utils.elapsed
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.Permissions.canRecordAudio
import com.ichi2.utils.UiUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// TODO : stop audio time view flickering

/**
 * This may be hosted in the [Reviewer], or in a [AudioVideoFragment]
 */
class AudioRecordingController(
    val context: Context,
    val linearLayout: LinearLayout? = null,
    val viewModel: MultimediaViewModel? = null,
    val note: IMultimediaEditableNote? = null,
) {
    private lateinit var audioRecorder: AudioRecorder
    private var state: RecordingState = AppendToRecording.CLEARED

    /**
     * It's Nullable and that it is set only if a sound is playing or paused, otherwise it is null.
     */
    private var audioPlayer: MediaPlayer? = null

    private lateinit var recordButton: MaterialButton

    /** optional in some layouts */
    private var saveButton: MaterialButton? = null

    /** Shows the time elapsed (00:00:00), optional in some layouts */
    private var audioTimeView: TextView? = null
    private lateinit var audioTimer: AudioTimer
    private lateinit var playAudioButton: MaterialButton
    private lateinit var forwardAudioButton: MaterialButton
    private lateinit var rewindAudioButton: MaterialButton
    private lateinit var audioWaveform: AudioWaveform
    private lateinit var audioProgressBar: LinearProgressIndicator
    private val isCleared
        get() = state == AppendToRecording.CLEARED || state == ImmediatePlayback.CLEARED
    private val isRecordingPaused
        get() = state == AppendToRecording.RECORDING_PAUSED
    private val isPlaying get() = state == AppendToRecording.PLAYBACK_PLAYING || state == ImmediatePlayback.PLAYBACK_PLAYING
    private lateinit var cancelAudioRecordingButton: MaterialButton
    private lateinit var playAudioButtonLayout: LinearLayout

    // Could be RelativeLayout, could be LinearLayout
    private lateinit var recordAudioButtonLayout: View
    private lateinit var discardRecordingButton: MaterialButton

    // wave layout takes up a lot of screen in HORIZONTAL layout so we need to hide it
    private var orientationEventListener: OrientationEventListener? = null

    init {
        Timber.d("Initializing the audio recorder UI")
        if (linearLayout != null) {
            createUI(context, linearLayout, AppendToRecording.CLEARED, R.layout.activity_audio_recording)
        }
    }

    fun createUI(
        context: Context,
        layout: LinearLayout,
        initialState: RecordingState,
        @LayoutRes controllerLayout: Int,
    ) {
        this.state = initialState
        audioRecorder = AudioRecorder(context)
        if (inEditField) {
            val origAudioPath = viewModel?.currentMultimediaPath?.value
            var bExist = false
            if (origAudioPath != null) {
                val f = origAudioPath
                if (f.exists()) {
                    tempAudioPath = f
                    bExist = true
                }
            }
            if (!bExist) {
                tempAudioPath = generateTempAudioFile(context)
            }
        }

        val layoutInflater = LayoutInflater.from(context)
        val inflatedLayout =
            layoutInflater.inflate(controllerLayout, null) as LinearLayout
        layout.addView(inflatedLayout, LinearLayout.LayoutParams.MATCH_PARENT)
        (context as? Activity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        recordAudioButtonLayout = layout.findViewById(R.id.record_buttons_layout)
        if (inEditField) {
            context.apply {
                // add preview of the field data to provide context to the user
                // use a separate scrollview to ensure that the content does not push the buttons off-screen when scrolled
                val sv = ScrollView(this)
                layout.addView(sv)
                val previewLayout = LinearLayout(this) // scrollView can only have one child
                previewLayout.orientation = LinearLayout.VERTICAL
                sv.addView(previewLayout)
                val label = FixedTextView(this)
                label.textSize = 20f
                label.text = UiUtil.makeBold(this.getString(R.string.audio_recording_field_list))
                label.gravity = Gravity.CENTER_HORIZONTAL
                previewLayout.addView(label)
                var hasTextContents = false

                if (note != null) {
                    for (i in 0 until note.initialFieldCount) {
                        val field = note.getInitialField(i)
                        FixedTextView(this).apply {
                            text = field?.text
                            textSize = 16f
                            setPaddingRelative(16, 0, 16, 24)
                            previewLayout.addView(this)
                        }
                        hasTextContents = hasTextContents or !field?.text.isNullOrBlank()
                    }
                    label.isVisible = hasTextContents
                }
            }
        }
        recordButton = layout.findViewById(R.id.action_start_recording)
        audioTimeView = layout.findViewById(R.id.audio_time_track)
        audioWaveform = layout.findViewById(R.id.audio_waveform_view)
        cancelAudioRecordingButton = layout.findViewById(R.id.action_cancel_recording)
        playAudioButton = layout.findViewById(R.id.action_play_recording)
        forwardAudioButton = layout.findViewById(R.id.action_forward)
        rewindAudioButton = layout.findViewById(R.id.action_rewind)
        playAudioButtonLayout = layout.findViewById(R.id.play_buttons_layout)
        audioProgressBar = layout.findViewById(R.id.audio_progress_indicator)
        val audioFileView = layout.findViewById<ShapeableImageView>(R.id.audio_file_imageview)
        discardRecordingButton = layout.findViewById(R.id.action_discard_recording)
        cancelAudioRecordingButton.isEnabled = false

        saveButton =
            layout.findViewById<MaterialButton?>(R.id.action_save_recording)?.apply {
                isEnabled = false
                setIconResource(R.drawable.ic_save_white)
                setOnClickListener {
                    Timber.i("'save' button clicked")
                    isAudioRecordingSaved = false
                    toggleSave()
                }
            }

        setUpMediaPlayer()

        audioTimer = setupAudioTimer(context)

        // if the recorder is in the 'cleared' state
        // holding the 'record' button should start a recording
        // releasing the 'record' button should complete the recording
        // TODO: remove haptics from the long press - the 'buzz' can be heard on the recording
        recordButton.setOnHoldListener(
            object : OnHoldListener {
                override fun onTouchStart() {
                    Timber.d("pressed 'record' button'")
                    controlAudioRecorder()
                }

                override fun onHoldEnd() {
                    Timber.d("finished holding 'record' button'")
                    if (state is ImmediatePlayback) {
                        controlAudioRecorder()
                    } else {
                        // if we're recording audio to add permanently,
                        // releasing the button after a long press should be 'save', not 'pause'
                        saveButton?.performClick()
                    }
                }
            },
        )

        playAudioButton.setOnClickListener {
            Timber.i("play/pause clicked")
            playPausePlayer()
        }

        cancelAudioRecordingButton.setOnClickListener {
            // a recording is in progress and is cancelled
            Timber.i("'clear recording' clicked")
            setupForNewRecording()
        }

        discardRecordingButton.setOnClickListener {
            // a recording has been completed, but we want to remake it
            Timber.i("'discard recording' clicked")
            discardAudio()
        }
        orientationEventListener =
            object : OrientationEventListener(context) {
                override fun onOrientationChanged(orientation: Int) {
                    // BUG: Executes on trivial orientation changes, not just portrait <-> landscape
                    when (context.resources.configuration.orientation) {
                        Configuration.ORIENTATION_LANDSCAPE -> {
                            audioFileView.visibility = View.GONE
                            audioWaveform.visibility = View.GONE
                        }
                        Configuration.ORIENTATION_PORTRAIT -> {
                            audioFileView.visibility = View.VISIBLE
                            audioWaveform.visibility = View.VISIBLE
                        }
                    }
                }
            }

        // only hide the views if in the 'append' layout
        if (state is AppendToRecording) {
            orientationEventListener?.enable()
        }

        (context as? Activity)?.let { activity ->
            activity.application.registerActivityLifecycleCallbacks(
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityCreated(
                        activity: Activity,
                        savedInstanceState: Bundle?,
                    ) {
                        // Not needed
                    }

                    override fun onActivityStarted(activity: Activity) {
                        // Not needed
                    }

                    override fun onActivityResumed(activity: Activity) {
                        // not needed
                    }

                    override fun onActivityPaused(activity: Activity) {
                        if (activity == context) {
                            onViewFocusChanged()
                        }
                    }

                    override fun onActivityStopped(activity: Activity) {
                        // Not needed
                    }

                    override fun onActivitySaveInstanceState(
                        activity: Activity,
                        outState: Bundle,
                    ) {
                        // Not needed
                    }

                    override fun onActivityDestroyed(activity: Activity) {
                        // not needed
                    }
                },
            )
        }
    }

    @CheckResult
    private fun setupAudioTimer(context: Context): AudioTimer {
        fun onRecordingTimerTick(duration: Duration) {
            if (isPlaying && !isRecording) {
                // This may remain at 0 for a few hundred ms while the audio player starts
                // BUG: It takes 300ms from elapsed == duration -> onCompletionListener being called
                // probably best to move onCompletionListener to here
                val elapsed = audioPlayer?.elapsed ?: Duration.ZERO
                audioProgressBar.progress = elapsed.inWholeMilliseconds.toInt()
                audioTimeView?.text = elapsed.formatAsString()
            } else {
                audioTimeView?.text = duration.formatAsString()
                audioProgressBar.progress = 0
            }
        }

        fun onRecordingAudioTick() {
            try {
                if (isRecording) {
                    val maxAmplitude = audioRecorder.getMaxAmplitude() / 10
                    audioWaveform.addAmplitude(maxAmplitude.toFloat())
                }
            } catch (e: IllegalStateException) {
                Timber.d(e, "Audio recorder interrupted")
            }
        }

        return AudioTimer(
            scope =
                (context as? LifecycleOwner)?.lifecycleScope
                    ?: CoroutineScope(Dispatchers.Main),
            onTimerTick = ::onRecordingTimerTick,
            onAudioTick = ::onRecordingAudioTick,
        )
    }

    private fun setUpMediaPlayer() {
        try {
            if (audioPlayer == null) {
                Timber.d("Creating media player for playback")
                audioPlayer = MediaPlayer()
            } else {
                Timber.d("Resetting media for playback")
                audioPlayer!!.reset()
            }
        } catch (e: IllegalStateException) {
            Timber.w(e, "Media Player couldn't be reset or already reset")
        }
    }

    /** Called on pause, and when 'done' is pressed */
    @NeedsTest("16321: record -> 'done' without pressing save")
    fun onViewFocusChanged() {
        Timber.i("activity paused: stopping recording/resetting player")
        if (isRecording || isRecordingPaused) {
            clearRecording()
        }
        if (isPlaying) {
            resetAudioPlayer()
        }
    }

    private fun setUiState(state: RecordingState) {
        // log the state transition
        Timber.i("ui: %s::%s -> %s::%s", this.state.javaClass.simpleName, this.state, state.javaClass.simpleName, state)

        this.state = state

        when (state) {
            ImmediatePlayback.CLEARED -> {
                recordButton.apply {
                    iconTint = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
                    strokeColor = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
                    setIconResource(R.drawable.ic_record)
                    contentDescription = context.getString(R.string.start_recording)
                }
                audioWaveform.clear()
                cancelAudioRecordingButton.isEnabled = false
                audioProgressBar.isVisible = false
            }
            ImmediatePlayback.RECORDING_IN_PROGRESS -> {
                recordButton.apply {
                    iconTint = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
                    strokeColor = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
                    setIconResource(R.drawable.ic_stop)
                    contentDescription = context.getString(R.string.stop_recording)
                }
                cancelAudioRecordingButton.isEnabled = true
                audioProgressBar.isVisible = false
            }
            ImmediatePlayback.PLAYBACK_PLAYING -> {
                recordButton.apply {
                    iconTint = ContextCompat.getColorStateList(context, R.color.audio_recorder_grey)
                    strokeColor = ContextCompat.getColorStateList(context, R.color.audio_recorder_grey)
                    setIconResource(R.drawable.ic_skip_next)
                    contentDescription = context.getString(R.string.next_recording)
                }
                cancelAudioRecordingButton.isEnabled = true
                audioProgressBar.isVisible = true
                audioWaveform.clear()
            }
            ImmediatePlayback.PLAYBACK_ENDED -> {
                recordButton.apply {
                    iconTint = ContextCompat.getColorStateList(context, R.color.audio_recorder_grey)
                    strokeColor = ContextCompat.getColorStateList(context, R.color.audio_recorder_grey)
                    setIconResource(R.drawable.ic_play)
                    contentDescription = context.getString(R.string.play_recording)
                }
                cancelAudioRecordingButton.isEnabled = true
                audioProgressBar.isVisible = true
                audioProgressBar.progress = 0
                audioWaveform.clear()
            }
            AppendToRecording.CLEARED -> {
                recordButton.apply {
                    iconTint = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
                    strokeColor = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
                    setIconResource(R.drawable.ic_record)
                }
                playAudioButton.apply {
                    setIconResource(R.drawable.round_play_arrow_24)
                    iconTint = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
                    strokeColor = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
                }
                cancelAudioRecordingButton.isEnabled = false
                audioTimeView?.text = DEFAULT_TIME
                saveButton?.isEnabled = false
                playAudioButtonLayout.visibility = View.GONE
                recordAudioButtonLayout.visibility = View.VISIBLE
                audioWaveform.clear()
            }
            AppendToRecording.RECORDING_IN_PROGRESS -> {
                cancelAudioRecordingButton.isEnabled = true
                saveButton?.isEnabled = true
                recordButton.setIconResource(R.drawable.round_pause_24)
                playAudioButtonLayout.visibility = View.GONE
                recordAudioButtonLayout.visibility = View.VISIBLE
            }
            AppendToRecording.RECORDING_PAUSED -> {
                recordButton.setIconResource(R.drawable.ic_record)
                playAudioButtonLayout.visibility = View.GONE
                recordAudioButtonLayout.visibility = View.VISIBLE
            }
            AppendToRecording.PLAYBACK_ENDED -> {
                audioWaveform.clear()
                saveButton?.isEnabled = false
                cancelAudioRecordingButton.isEnabled = false
                audioTimeView?.text = DEFAULT_TIME
                playAudioButtonLayout.visibility = View.VISIBLE
                recordAudioButtonLayout.visibility = View.GONE
                rewindAudioButton.isEnabled = false
                forwardAudioButton.isEnabled = false
                audioProgressBar.progress = 0
                playAudioButton.apply {
                    iconTint = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
                    strokeColor = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
                    setIconResource(R.drawable.round_play_arrow_24)
                }
            }
            AppendToRecording.PLAYBACK_PAUSED -> {
                rewindAudioButton.isEnabled = false
                forwardAudioButton.isEnabled = false
                playAudioButton.apply {
                    setIconResource(R.drawable.round_play_arrow_24)
                    iconTint = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
                    strokeColor = ContextCompat.getColorStateList(context, R.color.audio_recorder_red)
                }
            }
            AppendToRecording.PLAYBACK_PLAYING -> {
                rewindAudioButton.isEnabled = true
                forwardAudioButton.isEnabled = true
                playAudioButton.apply {
                    setIconResource(R.drawable.round_pause_24)
                    iconTint = ContextCompat.getColorStateList(context, R.color.audio_recorder_green)
                    strokeColor = ContextCompat.getColorStateList(context, R.color.audio_recorder_green)
                }
            }
        }
    }

    private fun discardAudio() {
        vibrate(20.milliseconds)
        viewModel?.updateCurrentMultimediaPath(null)
        setUiState(state.clear())
        tempAudioPath = generateTempAudioFile(context).also { tempAudioPath = it }
        stopAudioPlayer()
    }

    private fun stopAudioPlayer() {
        audioWaveform.clear()
        isRecording = false
        try {
            audioTimer.stop()
            audioPlayer?.stop()
            audioPlayer?.release()
        } catch (e: Exception) {
            Timber.w(e)
        }
        audioTimer = setupAudioTimer(context)
    }

    private fun prepareAudioPlayer() {
        audioPlayer = MediaPlayer()
        audioPlayer?.apply {
            tempAudioPath?.let { tempAudioPath -> setDataSource(tempAudioPath.absolutePath) }
            setOnPreparedListener {
                audioTimeView?.text = DEFAULT_TIME
            }
            prepareAsync()
        }
    }

    fun toggleSave(vibrate: Boolean = true) {
        Timber.i("recording completed")
        if (vibrate) vibrate(20.milliseconds)
        stopAndSaveRecording()
        // TODO: discuss if we want to keep the snackbar here
        // show this snackbar only in the edit field/multimedia activity
        if (inEditField) (context as Activity).showSnackbar(context.resources.getString(R.string.audio_saved))
        prepareAudioPlayer()
    }

    fun toggleToRecorder() {
        Timber.i("recorder requested")
        try {
            audioPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
            }
        } catch (e: IllegalStateException) {
            Timber.w(e, "MediaPlayer is not in a valid state to check if it's playing")
        }
        controlAudioRecorder()
    }

    /** When the 'primary' button of the audio recorder is pressed */
    private fun controlAudioRecorder() {
        if (!canRecordAudio(context)) {
            Timber.w("Audio recording permission denied.")
            showThemedToast(
                context,
                context.resources.getString(R.string.multimedia_editor_audio_permission_denied),
                true,
            )
            return
        }
        when (state) {
            is AppendToRecording -> {
                when {
                    isRecordingPaused -> resumeRecording()
                    isRecording -> pauseRecorder()
                    isCleared -> startRecording(tempAudioPath!!)
                    else -> startRecording(tempAudioPath!!)
                }
            }
            is ImmediatePlayback -> {
                when (state as ImmediatePlayback) {
                    ImmediatePlayback.CLEARED -> startRecording(tempAudioPath!!)
                    // end recording, allow a user to play or clear
                    ImmediatePlayback.RECORDING_IN_PROGRESS -> toggleSave(vibrate = false)
                    // stop -> playing
                    ImmediatePlayback.PLAYBACK_ENDED -> playPausePlayer()
                    // playing -> stop
                    ImmediatePlayback.PLAYBACK_PLAYING -> resetAudioPlayer()
                }
            }
        }
        vibrate(20.milliseconds)
    }

    private fun resetAudioPlayer() {
        Timber.i("saved recording: resetting")
        // Reset to 0; use pause() as seekTo() is not supported after stop()
        audioPlayer?.pause()
        audioPlayer?.seekTo(0)
        setUiState(state.ended())
        audioTimer.stop()
    }

    fun playPausePlayer() {
        audioProgressBar.max = audioPlayer?.duration ?: 0
        if (!audioPlayer!!.isPlaying) {
            Timber.i("saved recording: playing ")
            try {
                audioPlayer!!.start()
            } catch (e: Exception) {
                Timber.w(e, "error starting audioPlayer")
                showThemedToast(context, context.resources.getString(R.string.multimedia_editor_audio_view_playing_failed), true)
            }
            audioTimer.start()
            setUiState(state.play())
        } else {
            Timber.i("saved recording: pausing")
            setUiState(state.pausePlaying())
            audioTimer.pause()
            audioPlayer?.pause()
        }
        val shortAudioDuration = 5000
        rewindAudioButton.setOnClickListener {
            Timber.i("'back' pressed")
            val audioDuration = audioPlayer?.duration ?: 0
            if (audioDuration < shortAudioDuration) {
                audioPlayer?.seekTo(0)
                audioProgressBar.progress = 0
            } else {
                audioPlayer?.seekTo(audioPlayer!!.currentPosition - JUMP_VALUE)
                audioProgressBar.progress -= JUMP_VALUE
                audioTimer.start(audioPlayer!!.elapsed)
            }
        }
        forwardAudioButton.setOnClickListener {
            Timber.i("'forward' pressed")
            val audioDuration = audioPlayer?.duration ?: 0
            if (audioDuration < shortAudioDuration) {
                audioPlayer?.seekTo(audioDuration)
                audioProgressBar.progress = audioDuration
            } else {
                audioTimer.start(audioPlayer!!.elapsed)
                audioPlayer?.seekTo(audioPlayer!!.currentPosition + JUMP_VALUE)
                audioProgressBar.progress += JUMP_VALUE
            }
        }

        audioPlayer!!.setOnCompletionListener {
            Timber.i("saved recording: completed")
            audioTimer.stop()
            setUiState(state.ended())
        }
    }

    private fun startRecording(audioPath: File) {
        Timber.i("starting recording")
        try {
            audioRecorder.start(audioPath)
            isRecording = true
            audioTimer.start()
            setUiState(state.recording())
        } catch (e: Exception) {
            Timber.e(e, "Failed to start recording")
        }
    }

    private fun saveRecording() {
        viewModel?.updateCurrentMultimediaPath(tempAudioPath)
        tempAudioPath?.let { tempAudioPath ->
            viewModel?.updateMediaFileLength(tempAudioPath.length())
        }
    }

    fun stopAndSaveRecording() {
        audioTimer.stop()
        try {
            audioRecorder.stop()
        } catch (e: RuntimeException) {
            Timber.i(e, "Recording stop failed, this happens if stop was hit immediately after start")
            showThemedToast(context, context.resources.getString(R.string.multimedia_editor_audio_view_recording_failed), true)
        }
        isRecording = false
        isAudioRecordingSaved = true
        setUiState(state.afterSave())
        // save recording only in the edit field not in the reviewer but save it temporarily
        if (inEditField) saveRecording()
    }

    private fun pauseRecorder() {
        require(state is AppendToRecording) { "only supported if appending" }
        Timber.i("pausing recording")
        audioRecorder.pause()
        setUiState(AppendToRecording.RECORDING_PAUSED)
        audioTimer.pause()
    }

    private fun resumeRecording() {
        require(state is AppendToRecording) { "only supported if appending" }
        Timber.i("resuming recording")
        audioRecorder.resume()
        audioTimer.start()
        setUiState(AppendToRecording.RECORDING_IN_PROGRESS)
    }

    private fun clearRecording() {
        vibrate(20.milliseconds)
        audioTimer.stop()
        setUiState(state.clear())
        audioRecorder.stop()
        viewModel?.updateCurrentMultimediaPath(null)
        tempAudioPath = generateTempAudioFile(context).also { tempAudioPath = it }
        isRecording = false
    }

    fun onFocusLost() {
        audioRecorder.stop()
        audioPlayer!!.release()
    }

    fun onDestroy() {
        audioRecorder.close()
    }

    // when answer button is clicked in reviewer
    fun updateUIForNewCard() {
        Timber.i("resetting audio recorder: new card shown")
        try {
            setupForNewRecording()
        } catch (e: Exception) {
            Timber.d(e, "Unable to reset the audio recorder")
        }
    }

    private fun setupForNewRecording() {
        // transition to the 'CLEARED' state
        if (state == AppendToRecording.CLEARED) {
            return
        }
        if (isRecording || isRecordingPaused) {
            clearRecording()
        } else {
            discardAudio()
        }
    }

    /**
     * @see Compat.vibrate
     */
    private fun vibrate(duration: Duration) = compat.vibrate(context, duration, USAGE_TOUCH)

    companion object {
        var isRecording = false
        var isAudioRecordingSaved = false
        private var inEditField: Boolean = true
        const val DEFAULT_TIME = "00:00.00"
        const val JUMP_VALUE = 5000

        fun generateTempAudioFile(context: Context) =
            try {
                val storingDirectory = context.cacheDir
                File.createTempFile("ankidroid_audiorec", ".3gp", storingDirectory)
            } catch (e: IOException) {
                Timber.w(e, "Could not create temporary audio file.")
                null
            }

        fun setEditorStatus(inEditField: Boolean) {
            this.inEditField = inEditField
        }

        /** File of the temporary mic record  */
        var tempAudioPath: File? = null
    }

    sealed interface RecordingState {
        fun clear(): RecordingState = if (this is AppendToRecording) AppendToRecording.CLEARED else ImmediatePlayback.CLEARED

        fun recording(): RecordingState =
            if (this is AppendToRecording) AppendToRecording.RECORDING_IN_PROGRESS else ImmediatePlayback.RECORDING_IN_PROGRESS

        fun ended(): RecordingState = if (this is AppendToRecording) AppendToRecording.PLAYBACK_ENDED else ImmediatePlayback.PLAYBACK_ENDED

        fun afterSave() = ended()

        fun play(): RecordingState =
            if (this is AppendToRecording) AppendToRecording.PLAYBACK_PLAYING else ImmediatePlayback.PLAYBACK_PLAYING

        fun pausePlaying(): RecordingState =
            if (this is AppendToRecording) AppendToRecording.PLAYBACK_PAUSED else ImmediatePlayback.PLAYBACK_ENDED

        /**
         * The primary button is responsible for 'record', 'pause', 'append to recording'
         * A 'save' button is required before playback is enabled
         *
         * Designed for longer recordings of content to be saved on a note
         */
        enum class AppendToRecording : RecordingState {
            /** No recording has been made, or the recording was cleared */
            CLEARED,

            /** A recording is in progress */
            RECORDING_IN_PROGRESS,

            /** A recording has been made, and can be appended to */
            RECORDING_PAUSED,

            /** A completed recording is being listened to and is partially played */
            PLAYBACK_PAUSED,

            /** A completed recording is being listened to */
            PLAYBACK_PLAYING,

            /** A recording has been completed, and can be listened to */
            PLAYBACK_ENDED,
        }

        /**
         * The primary button is responsible for 'record', 'stop' and 'playback'
         * The secondary button clears the recording and allows for re-recording
         *
         * Designed for quick recordings in the Reviewer
         */
        enum class ImmediatePlayback : RecordingState {
            /** No recording has been made, or the recording was cleared */
            CLEARED,

            /** A recording is in progress */
            RECORDING_IN_PROGRESS,

            /** A completed recording has been made */
            PLAYBACK_ENDED,

            /** A completed recording is being listened to */
            PLAYBACK_PLAYING,
        }
    }
}
