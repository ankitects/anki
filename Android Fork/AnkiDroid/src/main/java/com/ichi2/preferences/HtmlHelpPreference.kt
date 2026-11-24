// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import android.content.Context
import android.text.method.LinkMovementMethod
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.text.parseAsHtml
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.ichi2.anki.R
import com.ichi2.anki.utils.ext.usingStyledAttributes

/**
 * Non-clickable preference that shows help text.
 *
 * The summary is parsed as a format string containing HTML,
 * and may contain up to three format specifiers as understood by `Sting.format()`,
 * arguments for which can be specified using XML attributes such as `app:substitution1`.
 *
 *     <com.ichi2.preferences.HtmlHelpPreference
 *         android:summary="@string/format_string"
 *         app:substitution1="@string/substitution1"
 *         />
 *
 * The summary HTML can contain all tags that TextViews can display,
 * including new lines and links. Raw HTML can be entered using the CDATA tag, e.g.
 *
 *     <string name="format_string"><![CDATA[<b>Hello, %s</b>]]></string>
 */
class HtmlHelpPreference(
    context: Context,
    attrs: AttributeSet?,
) : Preference(context, attrs) {
    init {
        isSelectable = false
        isPersistent = false
    }

    private val substitutions =
        context.usingStyledAttributes(attrs, R.styleable.HtmlHelpPreference) {
            arrayOf(
                getString(R.styleable.HtmlHelpPreference_substitution1),
                getString(R.styleable.HtmlHelpPreference_substitution2),
                getString(R.styleable.HtmlHelpPreference_substitution3),
            )
        }

    override fun getSummary() =
        super
            .getSummary()
            .toString()
            .format(*substitutions)
            .parseAsHtml()

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val summary = holder.findViewById(android.R.id.summary) as TextView
        summary.movementMethod = LinkMovementMethod.getInstance()
        summary.maxHeight = Int.MAX_VALUE
    }
}
