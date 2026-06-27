// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.ichi2.anki.analytics.AnalyticsConstants.Actions
import com.ichi2.anki.analytics.AnalyticsConstants.Category
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.common.destinations.PreferencesDestination
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.databinding.FragmentMoreBinding
import com.ichi2.anki.dialogs.help.ARG_MENU_ITEMS
import com.ichi2.anki.dialogs.help.HelpDialog
import com.ichi2.anki.dialogs.help.childHelpMenuItems
import com.ichi2.anki.dialogs.help.mainHelpMenuItems
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.IntentUtil
import dev.androidbroadcast.vbpd.viewBinding

/**
 * Full-screen "More" destination in the bottom navigation bar.
 * Shows Settings, Help items, and Support items in a sectioned list.
 *
 * Help items open the existing [HelpDialog] focused on their subsection.
 * Support items directly open their respective URLs.
 */
class MoreFragment : Fragment(R.layout.fragment_more) {
    private val binding by viewBinding(FragmentMoreBinding::bind)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val marketIntent = AnkiDroidApp.getMarketIntent(requireContext())

        binding.moreSettings.setOnClickListener {
            navigate(PreferencesDestination.Root)
        }

        // Help section: each opens HelpDialog with the relevant sub-items
        binding.moreHelpManual.setOnClickListener {
            UsageAnalytics.sendAnalyticsEvent(Category.LINK_CLICKED, Actions.OPENED_USING_ANKIDROID)
            openHelpSection(R.string.help_title_using_ankidroid)
        }
        binding.moreHelpGetHelp.setOnClickListener {
            UsageAnalytics.sendAnalyticsEvent(Category.LINK_CLICKED, Actions.OPENED_GET_HELP)
            openHelpSection(R.string.help_title_get_help)
        }
        binding.moreHelpCommunity.setOnClickListener {
            UsageAnalytics.sendAnalyticsEvent(Category.LINK_CLICKED, Actions.OPENED_COMMUNITY)
            openHelpSection(R.string.help_title_community)
        }
        binding.moreHelpPrivacy.setOnClickListener {
            UsageAnalytics.sendAnalyticsEvent(Category.LINK_CLICKED, Actions.OPENED_PRIVACY)
            openHelpSection(R.string.help_title_privacy)
        }

        // Support section: direct URL actions
        binding.moreSupportDonate.setOnClickListener {
            UsageAnalytics.sendAnalyticsEvent(Category.LINK_CLICKED, Actions.OPENED_DONATE)
            openUrl(getString(R.string.link_opencollective_donate))
        }
        binding.moreSupportTranslate.setOnClickListener {
            UsageAnalytics.sendAnalyticsEvent(Category.LINK_CLICKED, Actions.OPENED_TRANSLATE)
            openUrl(getString(R.string.link_translation))
        }
        binding.moreSupportDevelop.setOnClickListener {
            UsageAnalytics.sendAnalyticsEvent(Category.LINK_CLICKED, Actions.OPENED_DEVELOP)
            openUrl(getString(R.string.link_ankidroid_development_guide))
        }
        binding.moreSupportRate.setOnClickListener {
            UsageAnalytics.sendAnalyticsEvent(Category.LINK_CLICKED, Actions.OPENED_RATE)
            if (IntentUtil.canOpenIntent(requireContext(), marketIntent)) {
                startActivity(marketIntent)
            }
        }
        binding.moreSupportOther.setOnClickListener {
            UsageAnalytics.sendAnalyticsEvent(Category.LINK_CLICKED, Actions.OPENED_OTHER)
            openUrl(getString(R.string.link_contribution))
        }
        binding.moreSupportFeedback.setOnClickListener {
            UsageAnalytics.sendAnalyticsEvent(Category.LINK_CLICKED, Actions.OPENED_SEND_FEEDBACK)
            openUrl(AnkiDroidApp.feedbackUrl)
        }

        // Hide rate if Play Store not available
        if (!IntentUtil.canOpenIntent(requireContext(), marketIntent)) {
            binding.moreSupportRate.visibility = View.GONE
        }
    }

    /**
     * Opens [HelpDialog] showing the children of the help section matching [titleRes].
     * The parent ID is derived from [mainHelpMenuItems] to avoid hardcoded coupling.
     */
    private fun openHelpSection(
        @StringRes titleRes: Int,
    ) {
        val sectionId = mainHelpMenuItems.single { it.titleResId == titleRes }.id
        val children = childHelpMenuItems.filter { it.parentId == sectionId }
        val dialog =
            HelpDialog().apply {
                arguments =
                    Bundle().apply {
                        putInt(HelpDialog.ARG_MENU_TITLE, titleRes)
                        putParcelableArray(ARG_MENU_ITEMS, children.toTypedArray())
                    }
            }
        requireActivity().showDialogFragment(dialog)
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
