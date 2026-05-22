// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.compat

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import kotlin.time.Duration

/** Implementation of [Compat] for SDK level 31  */
@RequiresApi(31)
open class CompatV31 : CompatV29() {
    override fun vibrate(
        context: Context,
        duration: Duration,
        @VibrationUsage usage: Int,
    ) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val effect = VibrationEffect.createOneShot(duration.inWholeMilliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
        val vibrator = vibratorManager.defaultVibrator
        vibrator.vibrate(effect)
    }

    override fun getMediaRecorder(context: Context): MediaRecorder = MediaRecorder(context)

    override val webpLossyFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.WEBP_LOSSY
}
