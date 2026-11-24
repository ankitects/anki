// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.XmlRes
import androidx.core.view.ViewCompat
import androidx.core.view.ViewGroupCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.ime
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceManager.OnPreferenceTreeClickListener
import com.ichi2.anki.analytics.AnkiDroidUsageAnalytics
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.analytics.AnalyticsEvent
import com.ichi2.anki.common.analytics.getPreferenceReportableValue
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
        Analytics.send(AnalyticsEvent.SettingTapped(preference.key))
        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        if (key !in AnkiDroidUsageAnalytics.reportablePreferences) {
            return
        }
        if (key != null) {
            val valueToReport = getPreferenceReportableValue(sharedPreferences.get(key))
            Analytics.send(AnalyticsEvent.SettingChanged(key, valueToReport))
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

        ViewGroupCompat.installCompatInsetsDispatch(binding.root)
        val initialScrimVisibleHeightTrigger = binding.collapsingToolbarLayout.scrimVisibleHeightTrigger
        var previousTopInset = 0
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
            val bars = insets.getInsets(systemBars() or displayCutout() or ime())
            view.updatePadding(
                left = bars.left,
                right = bars.right,
            )
            val topInsetChange = bars.top - previousTopInset
            if (topInsetChange != 0) {
                binding.toolbar.updatePadding(top = bars.top)
                binding.toolbar.updateLayoutParams { height += topInsetChange }
                binding.collapsingToolbarLayout.updateLayoutParams { height += topInsetChange }
                // The scrim must still appear at the same scroll offset, even with a tall cutout.
                binding.collapsingToolbarLayout.scrimVisibleHeightTrigger = initialScrimVisibleHeightTrigger + bars.top
                previousTopInset = bars.top
            }
            listView.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        Analytics.sendAnalyticsScreenView(analyticsScreenNameConstant)
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
        dialogFragment.arguments = Bundle().apply { putString(ARG_PREFERENCE_KEY, preference.key) }
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
        const val ARG_PREFERENCE_KEY = "key"
    }
}

interface PreferenceXmlSource {
    @get:XmlRes
    val preferenceResource: Int
}
