// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.audiorecord

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentCheckPronunciationBinding
import com.ichi2.anki.ui.windows.reviewer.ReviewerViewModel
import com.ichi2.anki.utils.ext.collectInViewLifecycle
import com.ichi2.utils.show
import dev.androidbroadcast.vbpd.viewBinding

/**
 * Integrates [AudioRecordView] with [AudioPlayView] to play the recorded audios.
 */
class CheckPronunciationFragment : Fragment(R.layout.fragment_check_pronunciation) {
    private val viewModel: CheckPronunciationViewModel by viewModels()
    private val studyScreenViewModel: ReviewerViewModel by viewModels({ requireParentFragment() })

    private val binding by viewBinding(FragmentCheckPronunciationBinding::bind)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                AlertDialog.Builder(requireContext()).show {
                    setTitle(CommonString.permission_denied)
                    setMessage(CommonString.microphone_permission_denied_message)
                    setPositiveButton(CommonString.dialog_ok) { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", requireContext().packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    }
                    setNegativeButton(CommonString.dialog_cancel, null)
                }
            }
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupViewListeners()
        observeViewModel()
        observeStudyScreenViewModel()
    }

    override fun onPause() {
        super.onPause()
        if (requireActivity().isChangingConfigurations) {
            return
        }
        if (binding.recordView.isRecording) {
            binding.recordView.finishRecording()
        }
        viewModel.pausePlayback()
    }

    private fun setupViewListeners() {
        binding.playView.setButtonPressListener(
            object : AudioPlayView.ButtonPressListener {
                override fun onPlayButtonPressed() {
                    viewModel.onPlayOrReplay()
                }

                override fun onCancelButtonPressed() {
                    viewModel.onCancelPlayback()
                }
            },
        )

        binding.recordView.setRecordingListener(
            object : AudioRecordView.RecordingListener {
                override fun onRecordingPermissionRequired() {
                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                override fun onRecordingStarted() {
                    viewModel.onRecordingStarted()
                }

                override fun onRecordingCanceled() {
                    viewModel.onRecordingCancelled()
                }

                override fun onRecordingCompleted() {
                    viewModel.onRecordingCompleted()
                }
            },
        )
    }

    private fun observeViewModel() {
        viewModel.isPlaybackVisibleFlow.collectInViewLifecycle { isVisible ->
            binding.playView.isVisible = isVisible
            binding.recordView.setRecordDisplayVisibility(!isVisible)
        }
        viewModel.playbackProgressFlow.collectInViewLifecycle { progress ->
            binding.playView.setPlaybackProgress(progress)
        }
        viewModel.playbackProgressBarMaxFlow.collectInViewLifecycle { max ->
            binding.playView.setPlaybackProgressBarMax(max)
        }
        viewModel.isPlayingFlow.collectInViewLifecycle { isPlaying ->
            val iconRes = if (isPlaying) R.drawable.ic_replay else R.drawable.ic_play
            binding.playView.changePlayIcon(iconRes)
        }
        viewModel.replayFlow.collectInViewLifecycle {
            binding.playView.rotateReplayIcon()
        }
    }

    private fun observeStudyScreenViewModel() {
        studyScreenViewModel.voiceRecorderEnabledFlow.collectInViewLifecycle { isEnabled ->
            if (!isEnabled) {
                viewModel.resetAll()
                binding.recordView.forceReset()
            }
        }
        studyScreenViewModel.replayVoiceFlow.collectInViewLifecycle {
            viewModel.onPlayOrReplay()
        }
        studyScreenViewModel.onCardUpdatedFlow.collectInViewLifecycle {
            binding.playView.isVisible = false
            viewModel.onCancelPlayback()
            binding.recordView.setRecordDisplayVisibility(true)
        }
    }
}
