// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.account.AccountActivity
import com.ichi2.anki.common.crashreporting.runCatchingWithReport
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.ifNullOrEmpty
import com.ichi2.preferences.NumberRangePreferenceCompat
import com.ichi2.utils.show

/**
 * Fragment with preferences related to syncing
 */
class SyncSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_sync
    override val analyticsScreenNameConstant: String
        get() = "prefs.sync"

    override fun initSubscreen() {
        // AnkiWeb Account
        updateSyncAccountSummary()

        // Enable/disable one-way sync if the user is logged in
        updateOneWaySyncEnabledState()

        // Configure 'Network timeout'
        // TODO: add 'reset to default' functionality
        requirePreference<NumberRangePreferenceCompat>(R.string.sync_io_timeout_secs_key).apply {
            title = TR.preferencesNetworkTimeout()
            summaryProvider =
                Preference.SummaryProvider<EditTextPreference> {
                    runCatchingWithReport("network_timeout") {
                        TR.qtMiscSecond(this.getValue())
                    }.getOrNull() ?: getString(CommonString.pref__etc__summary__error)
                }
        }

        // Configure one-way sync option
        requirePreference<Preference>(R.string.one_way_sync_key).apply {
            setSummary(TR.preferencesOnNextSyncForceChangesIn())
            setOnPreferenceClickListener {
                AlertDialog.Builder(requireContext()).show {
                    setTitle(CommonString.one_way_sync_title)
                    setMessage(TR.preferencesOnNextSyncForceChangesIn())
                    setPositiveButton(CommonString.dialog_ok) { _, _ ->
                        launchCatchingTask {
                            withCol { modSchema(check = false) }
                            showSnackbar(CommonString.one_way_sync_confirmation, Snackbar.LENGTH_SHORT)
                        }
                    }
                    setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
                }
                false
            }
        }

        requirePreference<Preference>(R.string.sync_account_key).apply {
            title = TR.sentenceCase.ankiWebAccount
            setOnPreferenceClickListener {
                val accountActivityIntent = AccountActivity.getIntent(requireContext())
                startActivity(accountActivityIntent)
                true
            }
        }

        // Custom sync server
        requirePreference<Preference>(R.string.custom_sync_server_key).setSummaryProvider {
            customSyncBase() ?: getString(CommonString.custom_sync_server_summary_none_of_the_two_servers_used)
        }
    }

    private fun updateSyncAccountSummary() {
        requirePreference<Preference>(R.string.sync_account_key)
            .summary =
            Prefs.username.ifNullOrEmpty { getString(CommonString.sync_account_summ_logged_out) }
    }

    private fun updateOneWaySyncEnabledState() {
        val isLoggedIn = !Prefs.username.isNullOrEmpty()
        requirePreference<Preference>(R.string.one_way_sync_key).isEnabled = isLoggedIn
    }

    // TODO trigger the summary change from MyAccount.kt once it is migrated to a fragment
    override fun onResume() {
        // Trigger a summary update in case the user logged in/out on MyAccount activity
        updateSyncAccountSummary()
        updateOneWaySyncEnabledState()
        super.onResume()
    }

    private fun customSyncBase(): String? {
        if (!Prefs.isCustomSyncEnabled) return null
        return Prefs.customSyncUri?.ifEmpty { null }
    }
}
