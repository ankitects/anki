/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer.audiorecord

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.DecelerateInterpolator
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
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
