/*
 * Copyright (c) 2026 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.textview.MaterialTextView
import com.ichi2.anki.R
import com.ichi2.anki.utils.ext.usingStyledAttributes

/**
 * A card that can be used to link to related settings on other preference fragments.
 */
class RelatedSettingsPreference : Preference {
    private val links: Array<RelatedSettingLink>

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        layoutResource = R.layout.view_related_settings
        isSelectable = false // Prevent the whole card from being clickable

        links =
            context.usingStyledAttributes(attrs, R.styleable.RelatedSettingsPreference) {
                val titles = getTextArray(R.styleable.RelatedSettingsPreference_relatedTitles)
                val fragments = getTextArray(R.styleable.RelatedSettingsPreference_relatedFragments)
                if (titles != null && fragments != null) {
                    val size = minOf(titles.size, fragments.size)
                    Array(size) { i ->
                        RelatedSettingLink(
                            title = titles[i].toString(),
                            fragment = fragments[i].toString(),
                        )
                    }
                } else {
                    emptyArray()
                }
            }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val container = holder.findViewById(R.id.related_settings_links_container) as LinearLayout
        container.removeAllViews() // clear views when the ViewHolder recycles

        val inflater = LayoutInflater.from(context)
        links.forEach { link ->

            val linkView = inflater.inflate(R.layout.view_related_settings_item, container, false) as MaterialTextView
            linkView.apply {
                text = link.title
                setOnClickListener {
                    // set the preference `fragment` property so the navigation
                    // goes through `onPreferenceStartFragment` normally.
                    this@RelatedSettingsPreference.fragment = link.fragment
                    preferenceManager.onPreferenceTreeClickListener?.onPreferenceTreeClick(this@RelatedSettingsPreference)
                }
            }
            container.addView(linkView)
        }
    }
}

data class RelatedSettingLink(
    val title: String,
    val fragment: String,
)
