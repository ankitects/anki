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
 *
 * This file incorporates code from https://github.com/varunjohn/Audio-Recording-Animation
 * under the Apache License, Version 2.0.
 */
package com.ichi2.anki.ui.windows.reviewer.audiorecord

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.widget.ThemeUtils
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.util.TypedValueCompat
import androidx.core.view.isVisible
import com.ichi2.anki.R
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.compat.USAGE_TOUCH
import com.ichi2.anki.databinding.ViewAudioRecordBinding
import com.ichi2.utils.Permissions
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import com.ichi2.anki.common.android.R as CommonR

/**
 * A view that can serve as an audio recorder.
 *
 * The main functionalities work around the record button:
 * * Tap to start recording
 * * Long press to record while pressed
 *     * Swipe up to 'lock' and keep recording until the stop button is pressed
 *     * Swipe left to cancel the recording
 * * Check if the microphone permission has been granted before doing any action
 *
 * It also displays a recording icon and time
 */
class AudioRecordView : ConstraintLayout {
    private val binding = ViewAudioRecordBinding.inflate(LayoutInflater.from(context), this)

    // region Animations
    private val animBlink = AnimationUtils.loadAnimation(context, R.anim.blink)
    private val animJump = AnimationUtils.loadAnimation(context, R.anim.jump)
    private val animJumpFast = AnimationUtils.loadAnimation(context, R.anim.jump_fast)
    // endregion

    // region State & Logic
    private var state = ViewState.IDLE
    private var stopTrackingAction = false
    private var chronometerBase: Long = 0
    private val recordEnabledColor = context.getColor(CommonR.color.material_red_600)
    private val recordDisabledColor = ThemeUtils.getThemeAttrColor(context, R.attr.editTextDisabled)

    private var firstX = 0f
    private var firstY = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val dp = TypedValueCompat.dpToPx(1F, resources.displayMetrics)
    private val cancelOffset: Float
    private val cancelFadeOffset: Float
    private val lockOffset: Float

    val isRecording: Boolean
        get() = state == ViewState.RECORDING || state == ViewState.LOCKED

    private var recordingListener: RecordingListener? = null

    fun setRecordingListener(recordingListener: RecordingListener) {
        this.recordingListener = recordingListener
    }

    private lateinit var gestureDetector: GestureDetector
    // endregion

    constructor(context: Context) : this(context, null, 0, 0)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        this.clipChildren = false

        cancelOffset = max((resources.displayMetrics.widthPixels * 0.25f), 60f * dp)
        cancelFadeOffset = cancelOffset * 0.8f
        lockOffset = max((resources.displayMetrics.heightPixels * 0.25f), 80f * dp)

        setupTouchListener()
    }

    private enum class ViewState {
        IDLE,
        RECORDING,
        LOCKED,
    }

    enum class RecordingBehavior {
        CANCEL,
        LOCK,
        RELEASE,
    }

    interface RecordingListener {
        fun onRecordingPermissionRequired()

        fun onRecordingStarted()

        fun onRecordingCanceled()

        fun onRecordingCompleted()
    }

    private fun setupTouchListener() {
        gestureDetector =
            GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean {
                        if (!Permissions.canRecordAudio(context)) {
                            recordingListener?.onRecordingPermissionRequired()
                            return true
                        }
                        startRecording()
                        binding.recordButton
                            .animate()
                            .scaleX(1.25f)
                            .scaleY(1.25f)
                            .setDuration(150)
                            .start()
                        return true
                    }

                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        if (!Permissions.canRecordAudio(context)) return true
                        lock()
                        return true
                    }

                    override fun onLongPress(e: MotionEvent) {
                        if (!Permissions.canRecordAudio(context)) return
                        CompatHelper.compat.vibrate(context, 50.milliseconds, USAGE_TOUCH)
                        showCancelAndLockSliders()
                        firstX = e.rawX
                        firstY = e.rawY
                    }
                },
            )
        binding.recordButton.setOnTouchListener(gestureListener)
    }

    private val gestureListener =
        OnTouchListener { _, motionEvent ->
            gestureDetector.onTouchEvent(motionEvent)

            if (motionEvent.action == MotionEvent.ACTION_UP || motionEvent.action == MotionEvent.ACTION_CANCEL) {
                if (state == ViewState.IDLE) {
                    reset(animate = true)
                }
            }

            if (state == ViewState.RECORDING) {
                when (motionEvent.action) {
                    MotionEvent.ACTION_UP -> stopRecording(RecordingBehavior.RELEASE)
                    MotionEvent.ACTION_MOVE -> handleMove(motionEvent)
                }
            }
            true
        }

    private fun handleMove(motionEvent: MotionEvent) {
        if (stopTrackingAction) return

        val behavior = getBehaviorFromDirection(motionEvent.rawX, motionEvent.rawY)
        when (behavior) {
            RecordingBehavior.CANCEL -> translateX(motionEvent.rawX - firstX)
            RecordingBehavior.LOCK -> translateY(motionEvent.rawY - firstY)
            else -> {}
        }

        lastX = motionEvent.rawX
        lastY = motionEvent.rawY
    }

    /**
     * @return the behavior based on the predominant movement, which can be:
     * * [RecordingBehavior.LOCK] if it's a vertical movement to the top
     * * [RecordingBehavior.CANCEL] if it's an horizontal movement to the left
     * * `null` otherwise
     */
    private fun getBehaviorFromDirection(
        currentX: Float,
        currentY: Float,
    ): RecordingBehavior? {
        val motionX = abs(firstX - currentX)
        val motionY = abs(firstY - currentY)

        return when {
            motionY > motionX && currentY < firstY -> RecordingBehavior.LOCK
            motionX > motionY && currentX < firstX -> RecordingBehavior.CANCEL
            else -> null
        }
    }

    /**
     * Moves the record button and lock slider vertically based on [y].
     * If [lockOffset] is reached, the recording is locked and the vertical positions are reset.
     */
    private fun translateY(y: Float) {
        if (y < -lockOffset) {
            lock()
            binding.recordButton.translationY = 0f
            return
        }

        binding.layoutLock.visibility = VISIBLE
        binding.recordButton.translationY = y
        binding.layoutLock.translationY = y / 2
        binding.recordButton.translationX = 0f
    }

    /**
     * Moves the record button and cancel slider horizontally based on [x].
     * If [cancelOffset] is reached, the recording is canceled
     * and the horizontal positions are reset.
     */
    private fun translateX(x: Float) {
        if (x < -cancelOffset) {
            cancel()
            binding.recordButton.translationX = 0f
            binding.layoutSlideCancel.translationX = 0f
            return
        }

        val alpha = (cancelFadeOffset - abs(x)) / cancelFadeOffset
        binding.layoutSlideCancel.alpha = alpha.coerceIn(0f, 1f)

        binding.recordButton.translationX = x
        binding.layoutSlideCancel.translationX = x
        binding.layoutLock.translationY = 0f
        binding.recordButton.translationY = 0f

        if (abs(x) < binding.recordButton.width / 2) {
            binding.layoutLock.visibility = VISIBLE
        } else {
            binding.layoutLock.visibility = GONE
        }
    }

    private fun startRecording() {
        state = ViewState.RECORDING
        stopTrackingAction = false
        recordingListener?.onRecordingStarted()
        displayRunningRecord()
    }

    private fun showCancelAndLockSliders() {
        binding.recordButton
            .animate()
            .scaleX(1.8f)
            .scaleY(1.8f)
            .setDuration(200)
            .setInterpolator(OvershootInterpolator())
            .start()

        binding.layoutLock.visibility = VISIBLE
        binding.layoutSlideCancel.visibility = VISIBLE
        binding.lockArrowIcon.startAnimation(animJumpFast)
        binding.lockIcon.startAnimation(animJump)
    }

    /**
     * Sets the visibility of the record timer and icon to [isVisible]
     */
    fun setRecordDisplayVisibility(isVisible: Boolean) {
        binding.chronometer.isVisible = isVisible
        binding.recordingDisplayIcon.isVisible = isVisible
    }

    fun finishRecording() {
        if (isRecording) {
            stopRecording(RecordingBehavior.RELEASE)
        }
    }

    private fun displayRunningRecord() {
        setRecordDisplayVisibility(true)

        binding.recordingDisplayIcon.setColorFilter(recordEnabledColor)
        binding.recordingDisplayIcon.startAnimation(animBlink)

        binding.chronometer.base = if (chronometerBase > 0) chronometerBase else SystemClock.elapsedRealtime()
        binding.chronometer.isEnabled = true
        binding.chronometer.start()
    }

    private fun lock() {
        state = ViewState.LOCKED
        stopTrackingAction = true

        binding.recordIcon.setImageResource(R.drawable.ic_stop)
        binding.recordButton.animate().cancel()
        binding.recordButton.scaleX = 1f
        binding.recordButton.scaleY = 1f

        binding.recordButton.setOnTouchListener(null)
        binding.recordButton.setOnClickListener {
            stopRecording(RecordingBehavior.LOCK)
        }
        binding.layoutSlideCancel.visibility = GONE
        binding.layoutLock.visibility = GONE
    }

    private fun cancel() {
        stopTrackingAction = true
        stopRecording(RecordingBehavior.CANCEL)
    }

    private fun stopRecording(outcome: RecordingBehavior) {
        if (state != ViewState.RECORDING && state != ViewState.LOCKED) return

        val animateRelease = outcome == RecordingBehavior.RELEASE
        reset(animate = animateRelease)
        binding.chronometer.stop()

        when (outcome) {
            RecordingBehavior.CANCEL -> {
                recordingListener?.onRecordingCanceled()
            }
            RecordingBehavior.RELEASE, RecordingBehavior.LOCK -> {
                recordingListener?.onRecordingCompleted()
            }
        }
    }

    private fun reset(animate: Boolean) {
        state = ViewState.IDLE
        stopTrackingAction = false
        firstX = 0f
        firstY = 0f
        lastX = 0f
        lastY = 0f
        chronometerBase = 0

        binding.recordIcon.setImageResource(R.drawable.ic_action_mic)
        binding.recordButton.setOnClickListener(null)
        binding.recordButton.setOnTouchListener(gestureListener)

        if (animate) {
            binding.recordButton
                .animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(100)
                .setInterpolator(LinearInterpolator())
                .start()
        } else {
            binding.recordButton.animate().cancel()
            binding.recordButton.scaleX = 1f
            binding.recordButton.scaleY = 1f
            binding.recordButton.translationX = 0f
            binding.recordButton.translationY = 0f
        }

        binding.layoutSlideCancel.visibility = GONE
        binding.layoutLock.visibility = GONE
        binding.chronometer.visibility = INVISIBLE
        binding.recordingDisplayIcon.visibility = INVISIBLE

        // Reset the translation of the sliders to ensure they start from the correct position next time
        binding.layoutLock.translationY = 0f
        binding.layoutSlideCancel.translationX = 0f
        binding.layoutSlideCancel.alpha = 1f

        setRecordDisplayVisibility(true)
        binding.chronometer.base = SystemClock.elapsedRealtime()
        binding.chronometer.isEnabled = false
        binding.recordingDisplayIcon.setColorFilter(recordDisabledColor)
        binding.recordingDisplayIcon.clearAnimation()
        binding.lockArrowIcon.clearAnimation()
        binding.lockIcon.clearAnimation()
    }

    /**
     * Immediately stops all actions and animations, and returns the view to its initial state.
     */
    fun forceReset() {
        binding.chronometer.stop()
        binding.recordButton.clearAnimation()
        binding.recordingDisplayIcon.clearAnimation()
        reset(animate = false)
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val savedState = SavedState(superState)
        savedState.state = state
        savedState.chronometerBase = binding.chronometer.base
        return savedState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            this.state = state.state
            this.chronometerBase = state.chronometerBase
            when (this.state) {
                ViewState.LOCKED -> {
                    displayRunningRecord()
                    lock()
                }
                else -> reset(false)
            }
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private class SavedState : BaseSavedState {
        var state: ViewState = ViewState.IDLE
        var chronometerBase: Long = 0

        constructor(superState: Parcelable?) : super(superState)

        private constructor(source: Parcel) : super(source) {
            state = ViewState.valueOf(source.readString() ?: ViewState.IDLE.name)
            chronometerBase = source.readLong()
        }

        override fun writeToParcel(
            out: Parcel,
            flags: Int,
        ) {
            super.writeToParcel(out, flags)
            out.writeString(state.name)
            out.writeLong(chronometerBase)
        }

        companion object CREATOR : Parcelable.Creator<SavedState> {
            override fun createFromParcel(source: Parcel): SavedState = SavedState(source)

            override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
        }
    }
}
