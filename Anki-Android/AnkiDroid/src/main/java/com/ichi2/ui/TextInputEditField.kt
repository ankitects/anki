/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
