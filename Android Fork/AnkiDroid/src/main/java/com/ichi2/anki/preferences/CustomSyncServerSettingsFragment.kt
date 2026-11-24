// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.SyncPreferences
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.preferences.VersatileTextPreference
import com.ichi2.utils.show
import okhttp3.HttpUrl.Companion.toHttpUrl

class CustomSyncServerSettingsFragment : SettingsFragment() {
    override val preferenceResource = R.xml.preferences_custom_sync_server
    override val analyticsScreenNameConstant = "prefs.custom_sync_server"

    override fun initSubscreen() {
        listOf(
            R.string.custom_sync_server_collection_url_key,
        ).forEach {
            requirePreference<VersatileTextPreference>(it).continuousValidator =
                VersatileTextPreference.Validator { value ->
                    if (value.isNotEmpty()) value.toHttpUrl()
                }
        }

        requirePreference<VersatileTextPreference>(
            R.string.custom_sync_certificate_key,
        ).setOnPreferenceChangeListener { _, newValue: Any? ->
            val newCert = newValue as String

            // Empty string input causes the certificate to be unset in the backend, i.e., no error
            if (!CollectionManager.updateCustomCertificate(newCert)) {
                AlertDialog.Builder(requireContext()).show {
                    setTitle(CommonString.dialog_invalid_custom_certificate_title)
                    setMessage(CommonString.dialog_invalid_custom_certificate)
                    setPositiveButton(CommonString.dialog_ok) { _, _ -> }
                }
                return@setOnPreferenceChangeListener false
            }

            showSnackbar(CommonString.dialog_updated_custom_certificate)
            true
        }
    }

    // See discussion at https://github.com/ankidroid/Anki-Android/pull/12367#discussion_r967681337
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager.sharedPreferences
            ?.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferenceManager.sharedPreferences
            ?.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private val preferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (
                key == SyncPreferences.CUSTOM_SYNC_URI ||
                key == SyncPreferences.CUSTOM_SYNC_ENABLED
            ) {
                prefs.edit {
                    remove(SyncPreferences.PREF_CURRENT_SYNC_URI)
                }
            }
        }
}
