/*
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
