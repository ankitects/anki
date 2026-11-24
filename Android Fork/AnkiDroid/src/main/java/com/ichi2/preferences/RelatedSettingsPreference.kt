// SPDX-License-Identifier: GPL-3.0-or-later

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
                fun getValues(index: Int): Array<String> {
                    val resId = getResourceId(index, 0)
                    if (resId == 0) {
                        return getString(index)?.let { arrayOf(it) } ?: emptyArray()
                    }
                    return if (context.resources.getResourceTypeName(resId) == "array") {
                        getTextArray(index)?.map { it.toString() }?.toTypedArray() ?: emptyArray()
                    } else {
                        arrayOf(getString(index) ?: "")
                    }
                }

                val titles = getValues(R.styleable.RelatedSettingsPreference_relatedTitles)
                val fragments = getValues(R.styleable.RelatedSettingsPreference_relatedFragments)

                val size = minOf(titles.size, fragments.size)
                Array(size) { i ->
                    RelatedSettingLink(
                        title = titles[i],
                        fragment = fragments[i],
                    )
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
