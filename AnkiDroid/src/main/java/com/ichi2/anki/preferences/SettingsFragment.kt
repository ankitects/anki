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
package com.ichi2.anki.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.annotation.XmlRes
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceManager.OnPreferenceTreeClickListener
import com.ichi2.anki.analytics.AnalyticsConstants
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.databinding.FragmentSettingsBinding
import com.ichi2.preferences.DialogFragmentProvider
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

abstract class SettingsFragment :
    PreferenceFragmentCompat(),
    OnPreferenceTreeClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener,
    PreferenceXmlSource {
    /** @return The XML file which defines the preferences displayed by this PreferenceFragment
     */
    @get:XmlRes
    abstract override val preferenceResource: Int

    protected val binding by viewBinding(FragmentSettingsBinding::bind)

    abstract fun initSubscreen()

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        UsageAnalytics.sendAnalyticsEvent(
            category = AnalyticsConstants.Category.SETTING,
            action = AnalyticsConstants.Actions.TAPPED_SETTING,
            label = preference.key,
        )
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        if (key !in UsageAnalytics.preferencesWhoseChangesShouldBeReported) {
            return
        }
        if (key != null) {
            val valueToReport = getPreferenceReportableValue(sharedPreferences.get(key))
            UsageAnalytics.sendAnalyticsEvent(
                category = AnalyticsConstants.Category.SETTING,
                action = AnalyticsConstants.Actions.CHANGED_SETTING,
                value = valueToReport,
                label = key,
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        FragmentSettingsBinding
            .inflate(inflater, container, false)
            .apply {
                listContainer.addView(super.onCreateView(inflater, container, savedInstanceState))
            }.root

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val title = preferenceManager?.preferenceScreen?.title ?: ""
        binding.toolbar.apply {
            setTitle(title)
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        UsageAnalytics.sendAnalyticsScreenView(analyticsScreenNameConstant)
        addPreferencesFromResource(preferenceResource)
        initSubscreen()
    }

    protected abstract val analyticsScreenNameConstant: String

    @Suppress("deprecation") // setTargetFragment #9452
    // androidx.preference.PreferenceDialogFragmentCompat uses the deprecated method
    // `getTargetFragment()`, which throws if `setTargetFragment()` isn't used before.
    // While this isn't fixed on upstream, suppress the deprecation warning
    override fun onDisplayPreferenceDialog(preference: Preference) {
        val dialogFragment =
            (preference as? DialogFragmentProvider)?.makeDialogFragment()
                ?: return super.onDisplayPreferenceDialog(preference)
        Timber.d("displaying custom preference: ${dialogFragment::class.simpleName}")
        dialogFragment.arguments = Bundle().apply { putString(PREF_DIALOG_KEY, preference.key) }
        dialogFragment.setTargetFragment(this, 0)
        dialogFragment.show(parentFragmentManager, "androidx.preference.PreferenceFragment.DIALOG")
    }

    override fun onStart() {
        super.onStart()
        PreferenceManager
            .getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        super.onStop()
        PreferenceManager
            .getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    companion object {
        const val PREF_DIALOG_KEY = "key"

        /**
         * Converts a preference value to a numeric number that
         * can be reported to analytics, since analytics events only accept
         * [Int] as value ([UsageAnalytics.sendAnalyticsEvent]),
         * or null if it can't be converted.
         *
         * Boolean preferences will return 1 if true and 0 if false
         *
         * String preferences whose values are stored in a numeric format,
         * e.g. fullscreen mode whose values are "0", "1" and "2",
         * can have their values reported as well.
         * */
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        fun getPreferenceReportableValue(value: Any?): Int? =
            when (value) {
                is Int -> value
                is String ->
                    try {
                        value.toInt()
                    } catch (e: NumberFormatException) {
                        null
                    }
                is Boolean -> if (value) 1 else 0
                is Float -> value.toInt()
                is Long -> value.toInt()
                else -> null
            }
    }
}

interface PreferenceXmlSource {
    @get:XmlRes
    val preferenceResource: Int
}
