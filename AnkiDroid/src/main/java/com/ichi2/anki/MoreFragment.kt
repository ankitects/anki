// SPDX-FileCopyrightText: 2026 Shaan Narendran <shaannaren06@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.ichi2.anki.databinding.FragmentMoreBinding
import com.ichi2.anki.dialogs.help.ARG_MENU_ITEMS
import com.ichi2.anki.dialogs.help.HelpDialog
import com.ichi2.anki.dialogs.help.childHelpMenuItems
import com.ichi2.anki.dialogs.help.mainHelpMenuItems
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.IntentUtil
import dev.androidbroadcast.vbpd.viewBinding

/**
 * Full-screen "More" destination in the bottom navigation bar.
 * Shows Settings, Help items, and Support items in a sectioned list.
 *
 * Help items open the existing [HelpDialog] focused on their subsection.
 * Support items directly open their respective URLs.
 *
 * TODO: add UsageAnalytics calls matching AnalyticsConstants.Actions before wiring
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
            startActivity(PreferencesActivity.getIntent(requireContext()))
        }

        // Help section: each opens HelpDialog with the relevant sub-items
        binding.moreHelpManual.setOnClickListener {
            openHelpSection(R.string.help_title_using_ankidroid)
        }
        binding.moreHelpGetHelp.setOnClickListener {
            openHelpSection(R.string.help_title_get_help)
        }
        binding.moreHelpCommunity.setOnClickListener {
            openHelpSection(R.string.help_title_community)
        }
        binding.moreHelpPrivacy.setOnClickListener {
            openHelpSection(R.string.help_title_privacy)
        }

        // Support section: direct URL actions
        binding.moreSupportDonate.setOnClickListener {
            openUrl(getString(R.string.link_opencollective_donate))
        }
        binding.moreSupportTranslate.setOnClickListener {
            openUrl(getString(R.string.link_translation))
        }
        binding.moreSupportDevelop.setOnClickListener {
            openUrl(getString(R.string.link_ankidroid_development_guide))
        }
        binding.moreSupportRate.setOnClickListener {
            if (IntentUtil.canOpenIntent(requireContext(), marketIntent)) {
                startActivity(marketIntent)
            }
        }
        binding.moreSupportOther.setOnClickListener {
            openUrl(getString(R.string.link_contribution))
        }
        binding.moreSupportFeedback.setOnClickListener {
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
                    bundleOf(
                        HelpDialog.ARG_MENU_TITLE to titleRes,
                        ARG_MENU_ITEMS to children.toTypedArray(),
                    )
            }
        requireActivity().showDialogFragment(dialog)
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
