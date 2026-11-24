// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.text.method.LinkMovementMethod
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.core.text.parseAsHtml
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.destinations.ChangelogDestination
import com.ichi2.anki.common.destinations.navigate
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.databinding.FragmentAboutBinding
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.scheduling.Fsrs
import com.ichi2.anki.servicelayer.DebugInfoService
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.utils.IntentUtil
import com.ichi2.utils.TruncatedString
import com.ichi2.utils.VersionUtils.pkgVersionName
import com.ichi2.utils.copyToClipboard
import com.ichi2.utils.show
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.ankiweb.rsdroid.BuildConfig as BackendBuildConfig

class AboutFragment : Fragment(R.layout.fragment_about) {
    @VisibleForTesting
    val binding by viewBinding(FragmentAboutBinding::bind)

    private val aboutContributorsSpannable: CharSequence
        get() {
            val contributorsLink = getString(R.string.link_contributors)
            val contributingGuideLink = getString(R.string.link_contribution)
            val strRes =
                if (BuildConfig.SHOW_DONATE_LINKS) {
                    CommonString.about_contributors_description
                } else {
                    CommonString.about_contributors_description_no_donate
                }
            return getString(
                strRes,
                contributorsLink,
                contributingGuideLink,
            ).parseAsHtml()
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        binding.buildDate.text =
            SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "d MMM yyyy"))
                .format(Date(BuildConfig.BUILD_TIME))

        binding.version.text = pkgVersionName
        binding.backendVersion.text =
            "(anki " + BackendBuildConfig.ANKI_DESKTOP_VERSION + " / " + BackendBuildConfig.ANKI_COMMIT_HASH.subSequence(0, 8) + ")"
        binding.fsrsVersion.text = Fsrs.displayVersion ?.let { version ->
            "($version)"
        } ?: ""

        // Logo secret
        binding.appLogo.setOnClickListener(DeveloperOptionsSecretClickListener(this))

        // Contributors text
        binding.contributorsDescription.apply {
            text = aboutContributorsSpannable
            movementMethod = LinkMovementMethod.getInstance()
        }

        // License text
        val gplLicenseLink = getString(R.string.licence_wiki)
        val agplLicenseLink = getString(R.string.link_agpl_wiki)
        val sourceCodeLink = getString(R.string.link_source)
        val dependencyLicenseLink = getString(R.string.dependency_license_wiki)
        binding.licenseDescription.apply {
            text =
                (
                    getString(CommonString.license_description, gplLicenseLink, agplLicenseLink, sourceCodeLink) + "<br>" +
                        getString(CommonString.other_licenses, dependencyLicenseLink)
                ).parseAsHtml()
            movementMethod = LinkMovementMethod.getInstance()
        }

        // Donate text
        if (BuildConfig.SHOW_DONATE_LINKS) {
            val donateLink = getString(R.string.link_opencollective_donate)
            binding.donateDescription.apply {
                text = getString(CommonString.donate_description, donateLink).parseAsHtml()
                movementMethod = LinkMovementMethod.getInstance()
            }
        } else {
            binding.aboutDonateTitle.isVisible = false
            binding.donateDescription.isVisible = false
        }

        binding.rateAnkiDroid.setOnClickListener {
            IntentUtil.tryOpenIntent(requireActivity(), AnkiDroidApp.getMarketIntent(requireContext()))
        }

        binding.openChangelog.setOnClickListener {
            navigate(ChangelogDestination)
        }

        binding.copyDebugInfo.text = TR.sentenceCase.copyDebugInfo
        binding.copyDebugInfo.setOnClickListener { copyDebugInfo() }
    }

    /**
     * Copies debug info (from [DebugInfoService.getDebugInfo]) to the clipboard
     */
    private fun copyDebugInfo() {
        launchCatchingTask {
            val debugInfo =
                withContext(Dispatchers.IO) {
                    DebugInfoService.getDebugInfo(requireContext())
                }
            requireContext().copyToClipboard(
                TruncatedString.from(debugInfo),
                failureMessageId = CommonString.about_ankidroid_error_copy_debug_info,
            )
        }
    }

    /**
     * Click listener which enables developer options on release builds
     * if the user clicks it a minimum number of times
     */
    private class DeveloperOptionsSecretClickListener(
        val fragment: Fragment,
    ) : View.OnClickListener {
        private var clickCount = 0
        private val clickLimit = 6

        override fun onClick(view: View) {
            if (Prefs.isDeveloperOptionsEnabled) {
                return
            }
            if (++clickCount == clickLimit) {
                showEnableDeveloperOptionsDialog(view.context)
            }
        }

        /**
         * Shows a dialog to confirm if developer options should be enabled or not
         */
        fun showEnableDeveloperOptionsDialog(context: Context) {
            AlertDialog.Builder(context).show {
                setTitle(CommonString.dev_options_enabled_pref)
                setIcon(R.drawable.ic_warning)
                setMessage(CommonString.dev_options_warning)
                setPositiveButton(CommonString.dialog_ok) { _, _ -> enableDeveloperOptions(context) }
                setNegativeButton(CommonString.dialog_cancel) { _, _ -> clickCount = 0 }
                setCancelable(false)
            }
        }

        fun enableDeveloperOptions(context: Context) {
            Prefs.isDeveloperOptionsEnabled = true
            fragment.requireActivity().recreate()
            showThemedToast(context, R.string.dev_options_enabled_msg, shortLength = true)
        }
    }
}
