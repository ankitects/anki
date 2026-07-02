/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
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
 */
package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.ThemeUtils
import androidx.core.content.withStyledAttributes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.ichi2.anki.LanguageUtils
import com.ichi2.anki.R

/**
 * Preference used on the headers of [com.ichi2.anki.preferences.HeaderFragment]
 */
class HeaderPreference : Preference {
    private var isHighlighted = false

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, androidx.preference.R.attr.preferenceStyle)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : this(context, attrs, defStyleAttr, androidx.preference.R.style.Preference)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        context.withStyledAttributes(attrs, R.styleable.HeaderPreference) {
            val entries = getTextArray(R.styleable.HeaderPreference_summaryEntries)
            if (entries != null) {
                summary = buildHeaderSummary(*entries)
            }
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        if (isHighlighted) {
            val color = ThemeUtils.getThemeAttrColor(context, R.attr.currentDeckBackgroundColor)
            holder.itemView.setBackgroundColor(color)
        }
    }

    fun setHighlighted(highlight: Boolean) {
        isHighlighted = highlight
        notifyChanged()
    }

    companion object {
        /**
         * Join [entries] with ` • ` as separator
         * to build a summary string for some preferences categories
         * e.g. `foo`, `bar`, `hi` ->  `foo • bar • hi`
         */
        fun buildHeaderSummary(vararg entries: CharSequence): String =
            if (!LanguageUtils.appLanguageIsRTL()) {
                entries.joinToString(separator = " • ")
            } else {
                entries.reversed().joinToString(separator = " • ")
            }
    }
}
