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
package com.ichi2.anki.ui.windows.reviewer

import android.content.Context
import android.text.style.RelativeSizeSpan
import android.util.AttributeSet
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.R
import com.ichi2.anki.utils.ext.usingStyledAttributes

class AnswerButton : MaterialButton {
    private val easeName: String

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, com.google.android.material.R.attr.materialButtonStyle)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        easeName =
            context.usingStyledAttributes(attrs, R.styleable.AnswerButton) {
                requireNotNull(getString(R.styleable.AnswerButton_easeName)) {
                    "app:easeName value not set"
                }
            }

        val nextTime =
            context.usingStyledAttributes(attrs, R.styleable.AnswerButton) {
                getString(R.styleable.AnswerButton_nextTime)
            }

        setNextTime(nextTime)
    }

    fun setNextTime(nextTime: String?) {
        text =
            if (nextTime != null) {
                buildSpannedString {
                    inSpans(RelativeSizeSpan(0.8F)) {
                        append(nextTime)
                    }
                    append("\n")
                    append(easeName)
                }
            } else {
                easeName
            }
    }
}
