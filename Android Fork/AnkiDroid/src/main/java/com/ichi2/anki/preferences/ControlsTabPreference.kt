// SPDX-License-Identifier: GPL-3.0-or-later

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
