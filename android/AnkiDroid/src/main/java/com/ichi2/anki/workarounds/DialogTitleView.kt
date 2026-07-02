/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  This file incorporates work covered by the following copyright and
 *  permission notice:
 *
 *     Copyright (C) 2015 Google Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 * Changes:
 * * Converted to Kotlin
 * * Removed @RestrictTo(LIBRARY_GROUP_PREFIX)
 * * renamed: `DialogTitle` -> `DialogTitleView`
 * * Inherit from FixedTextView
 */

@file:Suppress("PackageDirectoryMismatch")

package androidx.appcompat.widget

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.R
import androidx.core.content.withStyledAttributes
import com.ichi2.ui.FixedTextView

/**
 * Used by dialogs to change the font size and number of lines to try to fit the text to the available space.
 *
 * Renamed from `DialogTitle` to avoid a conflict
 */
class DialogTitleView : FixedTextView {
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val layout = layout ?: return
        val lineCount = layout.lineCount
        if (lineCount <= 0) return

        val ellipsisCount = layout.getEllipsisCount(lineCount - 1)
        if (ellipsisCount == 0) return

        setSingleLine(false)
        setMaxLines(2)

        context.withStyledAttributes(
            null,
            R.styleable.TextAppearance,
            android.R.attr.textAppearanceMedium,
            android.R.style.TextAppearance_Medium,
        ) {
            val textSize = getDimensionPixelSize(R.styleable.TextAppearance_android_textSize, 0)
            if (textSize != 0) {
                // textSize is already expressed in pixels
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize.toFloat())
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
