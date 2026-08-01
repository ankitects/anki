/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.tabs.TabLayout
import com.ichi2.anki.R

class ControlsTabPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
        defStyleRes: Int = androidx.preference.R.style.Preference,
    ) : Preference(context, attrs, defStyleAttr, defStyleRes) {
        init {
            layoutResource = R.layout.preference_controls_tab
        }

        private var tabLayout: TabLayout? = null
        private var onTabSelectedListener: TabLayout.OnTabSelectedListener? = null

        fun setOnTabSelectedListener(listener: TabLayout.OnTabSelectedListener) {
            onTabSelectedListener?.let { oldListener ->
                tabLayout?.removeOnTabSelectedListener(oldListener)
            }
            onTabSelectedListener = listener
            tabLayout?.addOnTabSelectedListener(listener)
        }

        /**
         * Selects a tab programmatically by position.
         * @param tabPosition The position of the tab to select.
         */
        fun selectTab(tabPosition: Int) {
            tabLayout?.selectTab(tabLayout?.getTabAt(tabPosition))
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            tabLayout = holder.itemView as? TabLayout
            onTabSelectedListener?.let { listener ->
                tabLayout?.removeOnTabSelectedListener(listener)
                tabLayout?.addOnTabSelectedListener(listener)
            }
        }
    }
