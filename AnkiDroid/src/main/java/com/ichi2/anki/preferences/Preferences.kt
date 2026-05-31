/*
 * Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
 * Copyright (c) 2010 Norbert Nagold <norbert.nagold@gmail.com>
 * Copyright (c) 2012 Kostas Spyropoulos <inigo.aldana@gmail.com>
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
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
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.XmlRes
import androidx.core.os.bundleOf
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.bytehamster.lib.preferencesearch.SearchConfiguration
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.ichi2.anki.R
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.common.annotations.LegacyNotifications
import com.ichi2.anki.common.utils.android.getResFromAttr
import com.ichi2.anki.preferences.HeaderFragment.Companion.getHeaderKeyForFragment
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import com.ichi2.anki.utils.AnimUtils
import com.ichi2.anki.utils.isWindowCompact
import com.ichi2.utils.FragmentFactoryUtils
import timber.log.Timber
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

class PreferencesFragment :
    Fragment(R.layout.fragment_preferences),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    SearchPreferenceResultListener {
    /**
     * Whether the Settings view is split in two.
     * If so, the left side contains the list of all preference categories, and the right side contains the category currently opened.
     * Otherwise, the same view is used to show the list of categories first, and then one specific category.
     */
    private val settingsIsSplit get() = !resources.isWindowCompact()

    private val childFragmentOnBackPressedCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                Timber.i("back pressed - popping child backstack")
                childFragmentManager.popBackStack()
            }
        }

    private val childBackStackListener =
        FragmentManager.OnBackStackChangedListener {
            childFragmentOnBackPressedCallback.isEnabled = childFragmentManager.backStackEntryCount > 0
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        setupBackCallbacks()

        // Load initial subscreen if activity is being first created
        if (savedInstanceState == null) {
            loadInitialSubscreen()
        }

        setupBigScreenLayout()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        childFragmentManager.removeOnBackStackChangedListener(childBackStackListener)
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference,
    ): Boolean {
        val className = pref.fragment ?: return false
        val fragmentClass = FragmentFactory.loadFragmentClass(requireActivity().classLoader, className)

        // #18963: Remove any subscreens after opening a new primary screen
        if (settingsIsSplit && caller is HeaderFragment) {
            childFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.settings_container, fragmentClass, null)
            setFadeTransition(this)
            if (!settingsIsSplit || caller !is HeaderFragment) {
                addToBackStack(null)
            }
        }
        return true
    }

    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        if (result.key == getString(R.string.pref_review_reminders_screen_key)) {
            Timber.i("Preferences:: edit review reminders button pressed")
            val intent = ScheduleRemindersFragment.getIntent(requireContext(), ReviewReminderScope.Global)
            startActivity(intent)
            return
        }

        val fragment = getFragmentFromXmlRes(result.resourceFile) ?: return

        parentFragmentManager.popBackStack() // clear the search fragment from the backstack
        childFragmentManager.commit {
            replace(R.id.settings_container, fragment, fragment.javaClass.name)
            setFadeTransition(this)
            addToBackStack(fragment.javaClass.name)
        }

        if (fragment is ControlsSettingsFragment) {
            fragment.highlightPreference(result)
        } else {
            result.highlight(fragment as PreferenceFragmentCompat)
        }
    }

    private fun setupBackCallbacks() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, childFragmentOnBackPressedCallback)
        childFragmentManager.addOnBackStackChangedListener(childBackStackListener)
        childFragmentOnBackPressedCallback.isEnabled = childFragmentManager.backStackEntryCount > 0
    }

    private fun setupBigScreenLayout() {
        if (!settingsIsSplit) return

        // Configure the toolbars
        childFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    fragment: Fragment,
                    view: View,
                    savedInstanceState: Bundle?,
                ) {
                    // Make the collapsing toolbar look like a normal toolbar
                    view.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbarLayout)?.apply {
                        updateLayoutParams<AppBarLayout.LayoutParams> {
                            scrollFlags = 0
                            val resId = getResFromAttr(requireContext(), android.R.attr.actionBarSize)
                            height = resources.getDimensionPixelSize(resId)
                        }
                        isTitleEnabled = false
                        setContentScrimResource(android.R.color.transparent) // removes the collapsed scrim
                    }

                    // remove `Back` button from the toolbar of other fragments
                    if (fragment !is HeaderFragment) {
                        view.findViewById<MaterialToolbar>(R.id.toolbar)?.navigationIcon = null
                    }
                }
            },
            false,
        )

        childFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentCreated(
                    fm: FragmentManager,
                    fragment: Fragment,
                    savedInstanceState: Bundle?,
                ) {
                    if (fragment is HeaderFragment) return
                    val headerFragment = childFragmentManager.findFragmentById(R.id.lateral_nav_container)
                    val key = getHeaderKeyForFragment(fragment) ?: return
                    (headerFragment as? HeaderFragment)?.highlightPreference(key)
                }
            },
            false,
        )

        // Configure headers highlight
        childFragmentManager.addOnBackStackChangedListener {
            val headerFragment = childFragmentManager.findFragmentById(R.id.lateral_nav_container)
            if (headerFragment !is HeaderFragment) return@addOnBackStackChangedListener
            val fragment = childFragmentManager.findFragmentById(R.id.settings_container) ?: return@addOnBackStackChangedListener
            val key = getHeaderKeyForFragment(fragment) ?: return@addOnBackStackChangedListener
            headerFragment.highlightPreference(key)
        }
    }

    private fun setFadeTransition(fragmentTransaction: FragmentTransaction) {
        if (AnimUtils.areAnimationsEnabled(requireContext())) {
            fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
        }
    }

    /**
     * Starts the first settings fragment, which by default is [HeaderFragment].
     * The initial fragment may be overridden by putting the java class name
     * of the fragment on an intent extra with the key [INITIAL_FRAGMENT_EXTRA]
     */
    private fun loadInitialSubscreen() {
        val fragmentClassName = arguments?.getString(INITIAL_FRAGMENT_EXTRA)
        val initialFragment =
            if (fragmentClassName == null) {
                if (!settingsIsSplit) HeaderFragment() else GeneralSettingsFragment()
            } else {
                FragmentFactoryUtils.instantiate<Fragment>(requireActivity(), fragmentClassName)
            }
        childFragmentManager.commit {
            replace(R.id.settings_container, initialFragment, initialFragment::class.java.name)
        }
    }
}

/**
 * Host activity for [PreferencesFragment].
 *
 * Only necessary because [SearchConfiguration] demands an activity that implements
 * [SearchPreferenceResultListener].
 */
class PreferencesActivity :
    SingleFragmentActivity(),
    SearchPreferenceResultListener {
    override fun onSearchResultClicked(result: SearchPreferenceResult) {
        val fragment = supportFragmentManager.findFragmentByTag(FRAGMENT_TAG)
        if (fragment is SearchPreferenceResultListener) {
            fragment.onSearchResultClicked(result)
        }
    }

    companion object {
        fun getIntent(
            context: Context,
            initialFragment: KClass<out Fragment>? = null,
        ): Intent {
            val arguments = bundleOf(INITIAL_FRAGMENT_EXTRA to initialFragment?.jvmName)
            return Intent(context, PreferencesActivity::class.java).apply {
                putExtra(FRAGMENT_NAME_EXTRA, PreferencesFragment::class.jvmName)
                putExtra(FRAGMENT_ARGS_EXTRA, arguments)
            }
        }
    }
}

// Only enable AnkiDroid notifications unrelated to due reminders
@LegacyNotifications("Magic number which is no longer needed")
const val PENDING_NOTIFICATIONS_ONLY = 1000000

const val INITIAL_FRAGMENT_EXTRA = "initial_fragment"

/**
 * @return the [SettingsFragment] which uses the given [screen] resource.
 * i.e. [SettingsFragment.preferenceResource] value is the same of [screen]
 */
fun getFragmentFromXmlRes(
    @XmlRes screen: Int,
): SettingsFragment? =
    when (screen) {
        R.xml.preferences_general -> GeneralSettingsFragment()
        R.xml.preferences_reviewing -> ReviewingSettingsFragment()
        R.xml.preferences_sync -> SyncSettingsFragment()
        R.xml.preferences_backup_limits -> BackupLimitsSettingsFragment()
        R.xml.preferences_custom_sync_server -> CustomSyncServerSettingsFragment()
        R.xml.preferences_notifications -> NotificationsSettingsFragment()
        R.xml.preferences_appearance -> AppearanceSettingsFragment()
        R.xml.preferences_controls -> ControlsSettingsFragment()
        R.xml.preferences_reviewer_controls -> ControlsSettingsFragment()
        R.xml.preferences_previewer_controls -> ControlsSettingsFragment()
        R.xml.preferences_advanced -> AdvancedSettingsFragment()
        R.xml.preferences_accessibility -> AccessibilitySettingsFragment()
        R.xml.preferences_developer_options -> DeveloperOptionsFragment()
        R.xml.preferences_reviewer -> ReviewerOptionsFragment()
        R.xml.preferences_custom_buttons -> CustomButtonsSettingsFragment()
        else -> null
    }
