// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import com.ichi2.anki.R
import com.ichi2.anki.utils.ext.usingStyledAttributes
import com.ichi2.anki.utils.openUrl

/**
 * Extended version of [androidx.preference.PreferenceCategory] with the extra attributes:
 *
 * * app:helpLink (string): adds a help button that opens the provided link when tapped
 */
class ExtendedPreferenceCategory
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = androidx.preference.R.attr.preferenceCategoryStyle,
        defStyleRes: Int = androidx.preference.R.style.Preference_Category,
    ) : PreferenceCategory(context, attrs, defStyleAttr, defStyleRes) {
        private val helpLink: String?

        init {
            layoutResource = R.layout.preference_extended_category
            context.usingStyledAttributes(attrs, R.styleable.ExtendedPreferenceCategory) {
                helpLink = getString(R.styleable.ExtendedPreferenceCategory_helpLink)
            }
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)

            helpLink?.let { helpLink ->
                val uri = helpLink.toUri()
                val helpIcon = holder.findViewById(R.id.help_icon)
                helpIcon.isVisible = true
                helpIcon.setOnClickListener {
                    context.openUrl(uri)
                }
            }
        }
    }
