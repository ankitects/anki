// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.ThemeUtils
import androidx.core.content.withStyledAttributes
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.ichi2.anki.LanguageUtils
import com.ichi2.anki.R
import com.ichi2.anki.common.android.R as CommonR

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
            val color = ThemeUtils.getThemeAttrColor(context, CommonR.attr.selectedRowBackgroundColor)
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
