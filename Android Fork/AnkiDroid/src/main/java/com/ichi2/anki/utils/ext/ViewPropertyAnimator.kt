// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import android.view.ViewPropertyAnimator
import kotlin.time.Duration
import kotlin.time.DurationUnit

fun ViewPropertyAnimator.setDuration(duration: Duration): ViewPropertyAnimator = setDuration(duration.toLong(DurationUnit.MILLISECONDS))
