// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Akshay Jadhav <jadhavakshay0701@gmail.com>

package com.ichi2.anki.dialogs

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CommonString
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import java.util.function.Consumer

class WhiteBoardWidthDialog(
    private val context: Context,
    private var wbStrokeWidth: Int,
) {
    private var strokeWidthText: FixedTextView? = null
    var onStrokeWidthChanged: Consumer<Int>? = null
    private val seekBarChangeListener: OnSeekBarChangeListener =
        object : OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar,
                value: Int,
                b: Boolean,
            ) {
                wbStrokeWidth = value
                strokeWidthText!!.text = "" + value
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // intentionally blank
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // intentionally blank
            }
        }

    fun showStrokeWidthDialog() {
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPaddingRelative(6, 6, 6, 6)
        strokeWidthText = FixedTextView(context)
        strokeWidthText!!.gravity = Gravity.CENTER_HORIZONTAL
        strokeWidthText!!.textSize = 30f
        strokeWidthText!!.text = "" + wbStrokeWidth
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layout.addView(strokeWidthText, params)
        val seekBar = SeekBar(context)
        seekBar.progress = wbStrokeWidth
        seekBar.setOnSeekBarChangeListener(seekBarChangeListener)
        layout.addView(
            seekBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        AlertDialog.Builder(context).show {
            title(CommonString.whiteboard_stroke_width)
            positiveButton(CommonString.save) {
                onStrokeWidthChanged?.accept(wbStrokeWidth)
            }
            negativeButton(CommonString.dialog_cancel)
            setView(layout)
        }
    }

    fun onStrokeWidthChanged(c: Consumer<Int>?) {
        onStrokeWidthChanged = c
    }
}
