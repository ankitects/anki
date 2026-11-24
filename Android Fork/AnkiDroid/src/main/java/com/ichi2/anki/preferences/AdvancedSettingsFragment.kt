// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.MetaDB
import com.ichi2.anki.R
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.provider.CardContentProvider
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.startup.getDefaultAnkiDroidDirectory
import com.ichi2.anki.utils.openUrl
import com.ichi2.preferences.ExternalDirectorySelectionPreference
import com.ichi2.utils.Permissions
import com.ichi2.utils.Permissions.openAppSettingsScreen
import com.ichi2.utils.show
import timber.log.Timber
import java.io.File

class AdvancedSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_advanced
    override val analyticsScreenNameConstant: String
        get() = "prefs.advanced"

    private val microphonePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                requirePreference<SwitchPreferenceCompat>(R.string.pref_allow_template_audio_recording).isChecked = true
                return@registerForActivityResult
            }

            if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.RECORD_AUDIO)) {
                return@registerForActivityResult
            }

            AlertDialog.Builder(requireContext()).show {
                setTitle(CommonString.permission_denied)
                setMessage(CommonString.microphone_permission_denied_message)
                setPositiveButton(CommonString.dialog_ok) { _, _ ->
                    openAppSettingsScreen()
                }
                setNegativeButton(CommonString.dialog_cancel, null)
            }
        }

    override fun initSubscreen() {
        removeUnnecessaryAdvancedPrefs()

        // Check that input is valid before committing change in the collection path
        requirePreference<ExternalDirectorySelectionPreference>(CollectionHelper.PREF_COLLECTION_PATH).apply {
            setOnPreferenceChangeListener { _, newValue: Any? ->
                val newPath = newValue as String
                try {
                    CollectionHelper.initializeAnkiDroidDirectory(File(newPath))
                    launchCatchingTask {
                        CollectionManager.discardBackend()
                        val deckPicker = Intent(requireContext(), DeckPicker::class.java)
                        deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(deckPicker)
                    }
                    true
                } catch (e: StorageAccessException) {
                    // TODO: Request MANAGE_EXTERNAL_STORAGE
                    Timber.e(e, "Could not initialize directory: %s", newPath)
                    AlertDialog.Builder(requireContext()).show {
                        setTitle(CommonString.dialog_collection_path_not_dir)
                        setPositiveButton(CommonString.dialog_ok) { _, _ -> }
                        setNegativeButton(CommonString.reset_custom_buttons) { _, _ ->
                            value = getDefaultAnkiDroidDirectory(requireContext()).absolutePath
                        }
                    }
                    false
                }
            }
        }

        val ttsPref = requirePreference<SwitchPreferenceCompat>(R.string.tts_key)
        ttsPref.setOnPreferenceChangeListener { _, isChecked ->
            if (!(isChecked as Boolean)) return@setOnPreferenceChangeListener true
            AlertDialog.Builder(requireContext()).show {
                setIcon(R.drawable.ic_warning)
                setMessage(CommonString.readtext_deprecation_warn)
                setNegativeButton(CommonString.dialog_cancel) { _, _ -> ttsPref.isChecked = false }
                setNeutralButton(CommonString.scoped_storage_learn_more) { _, _ ->
                    ttsPref.isChecked = false
                    requireContext().openUrl(R.string.link_tts)
                }
                setPositiveButton(CommonString.dialog_ok) { _, _ -> }
                setOnCancelListener { ttsPref.isChecked = false }
            }
            return@setOnPreferenceChangeListener true
        }

        // Configure "Reset languages" preference
        requirePreference<Preference>(R.string.pref_reset_languages_key).setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext()).show {
                setTitle(CommonString.reset_languages)
                setIcon(R.drawable.ic_warning)
                setMessage(CommonString.reset_languages_question)
                setPositiveButton(CommonString.dialog_ok) { _, _ ->
                    if (MetaDB.resetLanguages(requireContext())) {
                        showSnackbar(CommonString.reset_confirmation)
                    }
                }
                setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
            }
            false
        }

        requirePreference<SwitchPreferenceCompat>(R.string.pref_allow_template_audio_recording).apply {
            isChecked = isChecked && Permissions.canRecordAudio(requireContext())
            setOnPreferenceChangeListener { _, newValue ->
                if (newValue !is Boolean) return@setOnPreferenceChangeListener false
                if (!newValue || Permissions.canRecordAudio(requireContext())) {
                    return@setOnPreferenceChangeListener true
                }
                // veto the opt-in until the permission is granted
                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                false
            }
        }

        /*
         * Plugins section
         */

        // Third party apps
        requirePreference<Preference>(R.string.thirdparty_apps_key).setOnPreferenceClickListener {
            requireContext().openUrl(R.string.link_third_party_api_apps)
            false
        }

        // Enable API
        requirePreference<SwitchPreferenceCompat>(R.string.enable_api_key).setOnPreferenceChangeListener { newValue ->
            val providerName = ComponentName(requireContext(), CardContentProvider::class.java.name)
            val state =
                if (newValue) {
                    Timber.i("AnkiDroid ContentProvider enabled by user")
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    Timber.i("AnkiDroid ContentProvider disabled by user")
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            requireActivity().packageManager.setComponentEnabledSetting(providerName, state, PackageManager.DONT_KILL_APP)
        }

        setupNewStudyScreenSettings()
    }

    private fun removeUnnecessaryAdvancedPrefs() {
        /* These preferences should be searchable or not based
         * on this same condition at [HeaderFragment.configureSearchBar] */
        // Disable the double scroll preference if no scrolling keys
        if (!CompatHelper.hasScrollKeys()) {
            val doubleScrolling = findPreference<SwitchPreferenceCompat>("double_scrolling")
            if (doubleScrolling != null) {
                preferenceScreen.removePreference(doubleScrolling)
            }
        }
    }

    private fun setupNewStudyScreenSettings() {
        if (!Prefs.isNewStudyScreenEnabled) return
        for (key in legacyStudyScreenSettings) {
            val keyString = getString(key)
            findPreference<Preference>(keyString)?.isVisible = false
        }
    }

    companion object {
        val legacyStudyScreenSettings =
            listOf(
                R.string.pref_reset_languages_key,
                R.string.double_scrolling_gap_key,
                R.string.tts_key,
            )
    }
}
