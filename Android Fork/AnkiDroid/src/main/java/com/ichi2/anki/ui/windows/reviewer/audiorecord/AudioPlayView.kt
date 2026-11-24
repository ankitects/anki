// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.audiorecord

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.DecelerateInterpolator
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.databinding.ViewAudioPlayBinding

/**
 * Simple player with a progress bar, a play button and a cancel button
 */
class AudioPlayView : ConstraintLayout {
    private val binding = ViewAudioPlayBinding.inflate(LayoutInflater.from(context), this)

    constructor(context: Context) : this(context, null, 0, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        binding.playButton.setOnClickListener {
            buttonPressListener?.onPlayButtonPressed()
        }
        binding.cancelButton.setOnClickListener {
            buttonPressListener?.onCancelButtonPressed()
        }
    }

    interface ButtonPressListener {
        fun onPlayButtonPressed()

        fun onCancelButtonPressed()
    }

    private var buttonPressListener: ButtonPressListener? = null

    fun setButtonPressListener(playListener: ButtonPressListener) {
        this.buttonPressListener = playListener
    }

    /**
     * Rotates the play icon 360º
     */
    fun rotateReplayIcon() {
        binding.playIconView.rotation = 0F
        binding.playIconView
            .animate()
            .rotation(-360F)
            .setDuration(400)
            .setInterpolator(
                DecelerateInterpolator(),
            ).start()
    }

    /**
     * Replaces the `Play` button icon with [iconRes] with a crossfade animation.
     */
    fun changePlayIcon(
        @DrawableRes iconRes: Int,
    ) {
        binding.playButton.contentDescription =
            context.getString(if (iconRes == R.drawable.ic_replay) CommonString.replay_voice else CommonString.play_recording)
        binding.playIconView
            .animate()
            .alpha(0f)
            .setDuration(100)
            .withEndAction {
                binding.playIconView.setImageResource(iconRes)
                binding.playIconView
                    .animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }.start()
    }

    fun setPlaybackProgress(progress: Int) {
        if (progress == 0) {
            binding.progressBar.progress = 0 // `animate = false` wasn't working for some reason
        } else {
            binding.progressBar.setProgress(progress, true)
        }
    }

    fun setPlaybackProgressBarMax(max: Int) {
        binding.progressBar.max = max
    }
}
