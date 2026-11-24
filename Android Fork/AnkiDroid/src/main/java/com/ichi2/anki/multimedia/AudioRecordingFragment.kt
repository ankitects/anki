// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multimedia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.time.formatAsString
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.compat.CompatHelper.Companion.compat
import com.ichi2.anki.compat.USAGE_TOUCH
import com.ichi2.anki.databinding.ActivityAudioRecordingBinding
import com.ichi2.anki.databinding.FragmentAudioRecordingBinding
import com.ichi2.anki.noteeditor.AudioRecordingViewModel
import com.ichi2.anki.noteeditor.AudioRecordingViewModel.Failure
import com.ichi2.anki.recorder.PlayerState
import com.ichi2.anki.recorder.RecorderState
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.OnHoldListener
import com.ichi2.anki.ui.setOnHoldListener
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.FileUtil
import com.ichi2.utils.Permissions
import com.ichi2.utils.UiUtil
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

class AudioRecordingFragment : MultimediaFragment(R.layout.fragment_audio_recording) {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal val binding by viewBinding(FragmentAudioRecordingBinding::bind)

    private val recordingViewModel: AudioRecordingViewModel by viewModels()

    private var recorderBinding: ActivityAudioRecordingBinding? = null

    override val title: String
        get() = resources.getString(CommonString.multimedia_editor_field_editing_audio)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ankiCacheDirectory = FileUtil.getAnkiCacheDirectory(requireContext(), "temp-media")
        if (ankiCacheDirectory == null) {
            Timber.e("createUI() failed to get cache directory")
            showErrorDialog(errorMessage = resources.getString(CommonString.multimedia_editor_failed))
            return
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (view == null) return@registerForActivityResult
            if (isGranted) {
                Timber.d("Audio permission granted")
                initializeAudioRecorder()
                setupDoneButton()
            } else {
                Timber.d("Audio permission denied")
                showErrorDialog(resources.getString(CommonString.multimedia_editor_audio_permission_refused))
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        if (!hasMicPermission()) {
            return
        }

        initializeAudioRecorder()
        setupDoneButton()
    }

    override fun onStop() {
        super.onStop()
        recorderBinding?.root?.keepScreenOn = false
        if (!requireActivity().isChangingConfigurations) {
            recordingViewModel.interrupt()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recorderBinding?.applyOrientation(newConfig)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recorderBinding = null
    }

    private fun hasMicPermission(): Boolean {
        if (!Permissions.canRecordAudio(requireContext())) {
            Timber.i("Requesting Audio Permissions")
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return false
        }
        return true
    }

    private fun setupDoneButton() {
        binding.actionDone.setOnClickListener {
            Timber.d("AudioRecordingFragment:: Done button pressed")
            recordingViewModel.consumeRecording { file ->
                field.mediaFile = file
                field.hasTemporaryMedia = true
                setMultimediaResultAndFinish(MultimediaResult.Success(indexValue, field))
            }
        }
    }

    private fun initializeAudioRecorder() {
        if (recorderBinding != null) return
        Timber.d("Initialising the audio recorder")
        val recorder = ActivityAudioRecordingBinding.inflate(layoutInflater, binding.audioRecorderLayout, true)
        recorderBinding = recorder
        addFieldPreview()
        recorder.applyOrientation(resources.configuration)
        recorder.setupListeners()
        recorder.observeViewModel()
    }

    private fun addFieldPreview() {
        val context = requireContext()
        val scrollView = ScrollView(context)
        binding.audioRecorderLayout.addView(scrollView)
        val previewLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(previewLayout)
        val label =
            FixedTextView(context).apply {
                textSize = 20f
                text = UiUtil.makeBold(getString(CommonString.audio_recording_field_list))
                gravity = Gravity.CENTER_HORIZONTAL
            }
        previewLayout.addView(label)
        var hasTextContents = false
        for (i in 0 until note.initialFieldCount) {
            val field = note.getInitialField(i)
            FixedTextView(context).apply {
                text = field?.text
                textSize = 16f
                setPaddingRelative(16, 0, 16, 24)
                previewLayout.addView(this)
            }
            hasTextContents = hasTextContents or !field?.text.isNullOrBlank()
        }
        label.isVisible = hasTextContents
    }

    private fun ActivityAudioRecordingBinding.setupListeners() {
        actionStartRecording.isHapticFeedbackEnabled = false
        actionStartRecording.setOnHoldListener(
            object : OnHoldListener {
                override fun onTouchStart() {
                    Timber.i("'record' pressed")
                    onRecordPressed()
                }

                override fun onHoldEnd() {
                    Timber.i("'record' released after holding")
                    saveRecording()
                }
            },
        )
        actionSaveRecording.setIconResource(R.drawable.ic_save_white)
        actionSaveRecording.setOnClickListener {
            Timber.i("'save' clicked")
            saveRecording()
        }
        actionCancelRecording.setOnClickListener {
            Timber.i("'cancel recording' clicked")
            discardRecording()
        }
        actionDiscardRecording.setOnClickListener {
            Timber.i("'discard recording' clicked")
            discardRecording()
        }
        actionPlayRecording.setOnClickListener {
            Timber.i("play/pause clicked")
            togglePlayback()
        }
        actionRewind.setOnClickListener {
            Timber.i("'back' clicked")
            recordingViewModel.skipBack()
        }
        actionForward.setOnClickListener {
            Timber.i("'forward' clicked")
            recordingViewModel.skipForward()
        }
    }

    private fun onRecordPressed() {
        when (recordingViewModel.recorderState.value) {
            RecorderState.Idle -> recordingViewModel.startRecording()
            RecorderState.Recording -> {
                recordingViewModel.pauseRecording()
                vibrate()
            }
            RecorderState.Paused -> recordingViewModel.resumeRecording()
            is RecorderState.Recorded -> {}
        }
    }

    private fun saveRecording() {
        val state = recordingViewModel.recorderState.value
        if (state != RecorderState.Recording && state != RecorderState.Paused) return
        recordingViewModel.stopRecording()
        vibrate()
        if (recordingViewModel.recorderState.value is RecorderState.Recorded) {
            showSnackbar(CommonString.audio_saved)
        }
    }

    private fun discardRecording() {
        recordingViewModel.discard()
        vibrate()
    }

    private fun togglePlayback() {
        val state = recordingViewModel.playerState.value
        if (state is PlayerState.Ready && state.isPlaying) {
            recordingViewModel.pausePlayback()
        } else {
            recordingViewModel.play()
        }
    }

    private fun ActivityAudioRecordingBinding.observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    recordingViewModel.recorderState
                        .combine(recordingViewModel.playerState) { recorderState, playerState -> recorderState to playerState }
                        .collect { (recorderState, playerState) -> render(recorderState, playerState) }
                }
                launch {
                    recordingViewModel.elapsed.collect { elapsed ->
                        if (recordingViewModel.recorderState.value !is RecorderState.Recorded) {
                            audioTimeTrack.text = elapsed.formatAsString()
                        }
                    }
                }
                launch {
                    recordingViewModel.playbackPosition.collect { position ->
                        if (recordingViewModel.recorderState.value is RecorderState.Recorded) {
                            audioTimeTrack.text = position.formatAsString()
                            audioProgressIndicator.progress = position.inWholeMilliseconds.toInt()
                        }
                    }
                }
                launch {
                    recordingViewModel.amplitudes.collect { amplitude ->
                        if (amplitude != null) audioWaveformView.addAmplitude((amplitude / 10).toFloat())
                    }
                }
                launch {
                    recordingViewModel.failure.filterNotNull().collect { failure ->
                        showFailure(failure)
                        recordingViewModel.failureShown()
                    }
                }
            }
        }
    }

    private fun ActivityAudioRecordingBinding.render(
        recorderState: RecorderState,
        playerState: PlayerState,
    ) {
        val isRecorded = recorderState is RecorderState.Recorded
        val isRecordingOrPaused = recorderState == RecorderState.Recording || recorderState == RecorderState.Paused
        val isPlaying = playerState is PlayerState.Ready && playerState.isPlaying

        recordButtonsLayout.isVisible = !isRecorded
        playButtonsLayout.isVisible = isRecorded
        actionCancelRecording.isEnabled = isRecordingOrPaused
        actionSaveRecording.isEnabled = isRecordingOrPaused
        actionStartRecording.setIconResource(
            if (recorderState == RecorderState.Recording) R.drawable.round_pause_24 else R.drawable.ic_record,
        )
        if (!isRecordingOrPaused) audioWaveformView.clear()

        actionRewind.isEnabled = isPlaying
        actionForward.isEnabled = isPlaying
        actionPlayRecording.setIconResource(if (isPlaying) R.drawable.round_pause_24 else R.drawable.round_play_arrow_24)
        val playTint =
            ContextCompat.getColorStateList(requireContext(), if (isPlaying) R.color.audio_recorder_green else R.color.audio_recorder_red)
        actionPlayRecording.iconTint = playTint
        actionPlayRecording.strokeColor = playTint
        audioProgressIndicator.max =
            ((playerState as? PlayerState.Ready)?.duration?.inWholeMilliseconds ?: 0).toInt().coerceAtLeast(1)
        val position = recordingViewModel.playbackPosition.value
        audioProgressIndicator.progress = position.inWholeMilliseconds.toInt()
        audioTimeTrack.text = (if (isRecorded) position else recordingViewModel.elapsed.value).formatAsString()

        root.keepScreenOn = recorderState == RecorderState.Recording || isPlaying
        binding.actionDone.isEnabled = isRecorded
        viewModel.updateCurrentMultimediaPath((recorderState as? RecorderState.Recorded)?.file)
    }

    private fun showFailure(failure: Failure) {
        val message =
            when (failure) {
                Failure.RECORDING -> CommonString.multimedia_editor_audio_view_recording_failed
                Failure.PLAYBACK -> CommonString.multimedia_editor_audio_view_playing_failed
            }
        showThemedToast(requireContext(), getString(message), true)
    }

    private fun ActivityAudioRecordingBinding.applyOrientation(configuration: Configuration) {
        when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                audioFileImageview.isVisible = false
                audioWaveformView.isVisible = false
            }
            Configuration.ORIENTATION_PORTRAIT -> {
                audioFileImageview.isVisible = true
                audioWaveformView.isVisible = true
            }
        }
    }

    private fun vibrate() = compat.vibrate(requireContext(), 20.milliseconds, USAGE_TOUCH)

    companion object {
        fun getIntent(
            context: Context,
            multimediaExtra: MultimediaActivityExtra,
        ): Intent =
            MultimediaActivity.getIntent(
                context,
                AudioRecordingFragment::class,
                multimediaExtra,
            )
    }
}
