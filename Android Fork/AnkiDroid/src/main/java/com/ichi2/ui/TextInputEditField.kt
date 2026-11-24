// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.autofill.AutofillValue
import androidx.annotation.RequiresApi
import com.google.android.material.textfield.TextInputEditText

class TextInputEditField : TextInputEditText {
    @RequiresApi(Build.VERSION_CODES.O)
    private var autoFillListener: AutoFillListener? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun autofill(value: AutofillValue) {
        super.autofill(value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (autoFillListener != null) {
                autoFillListener!!.onAutoFill(value)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun interface AutoFillListener {
        fun onAutoFill(value: AutofillValue)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun setAutoFillListener(listener: AutoFillListener) {
        autoFillListener = listener
    }
}
