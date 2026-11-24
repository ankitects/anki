// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.ichi2.anki.R
import kotlin.properties.Delegates.observable

/**
 * A preference that shows text in a small box on the end, as set via [widgetText].
 */
class TextWidgetPreference(
    context: Context,
    attrs: AttributeSet?,
) : Preference(context, attrs) {
    init {
        widgetLayoutResource = R.layout.preference_widget_text
    }

    private var widget: TextView? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        widget = holder.findViewById(android.R.id.text1) as TextView
        widget?.text = widgetText
    }

    var widgetText: CharSequence?
        by observable(null) { _, _, value -> widget?.text = value }
}
