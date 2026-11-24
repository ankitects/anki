// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileSwitchGuard
import com.ichi2.anki.multiprofile.backupCheck
import com.ichi2.anki.multiprofile.mediaSyncCheck
import com.ichi2.anki.multiprofile.safeRestartApp
import com.ichi2.anki.multiprofile.syncCheck
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * State holder for [SwitchProfilesFragment]. Keeps the dialog visibility out
 * of the view layer so it survives configuration changes.
 */
class SwitchProfilesViewModel(
    private val profileManager: ProfileManager,
    private val switchGuard: ProfileSwitchGuard,
    private val restartApp: suspend () -> Unit,
) : ViewModel() {
    /** Profiles shown in the list. */
    val profiles: StateFlow<List<ProfileItem>>
        field = MutableStateFlow(profileManager.profileItems())

    val isAddProfileDialogVisible: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val profileBeingRenamed: StateFlow<ProfileItem?>
        field = MutableStateFlow<ProfileItem?>(null)

    val profileToSwitchTo: StateFlow<ProfileItem?>
        field = MutableStateFlow<ProfileItem?>(null)

    val isSwitchBlocked: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val profileSwitchFailed: StateFlow<ProfileItem?>
        field = MutableStateFlow<ProfileItem?>(null)

    fun showAddProfileDialog() {
        isAddProfileDialogVisible.value = true
    }

    fun dismissAddProfileDialog() {
        isAddProfileDialogVisible.value = false
    }

    /** Called when the user confirms a valid name in the add-profile dialog. */
    fun addProfile(name: ProfileName) {
        isAddProfileDialogVisible.value = false
        Timber.i("Add profile confirmed (%d chars)", name.value.length)
        profileManager.createNewProfile(name)
        refresh()
    }

    fun showRenameProfileDialog(profile: ProfileItem) {
        profileBeingRenamed.value = profile
    }

    fun dismissRenameProfileDialog() {
        profileBeingRenamed.value = null
    }

    fun renameProfile(name: ProfileName) {
        val profile = profileBeingRenamed.value ?: return
        profileBeingRenamed.value = null
        Timber.i("Rename profile confirmed: %s", profile.id)
        profileManager.renameProfile(profile.id, name)
        refresh()
    }

    fun requestSwitch(profile: ProfileItem) {
        if (profileManager.isSwitching || profileManager.isActive(profile.id)) return
        profileToSwitchTo.value = profile
    }

    fun dismissSwitchProfileDialog() {
        profileToSwitchTo.value = null
    }

    fun switchProfile() {
        val profile = profileToSwitchTo.value ?: return
        profileToSwitchTo.value = null
        if (!profileManager.tryStartSwitch()) return
        Timber.i("Switch profile confirmed: %s", profile.id)
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                when (val result = withContext(ioDispatcher) { switchGuard(profile.id) }) {
                    ProfileSwitchGuard.Result.Success -> restartInto(profile)
                    is ProfileSwitchGuard.Result.Blocked -> {
                        Timber.i("Profile switch blocked: %s", result.reasons)
                        profileManager.endSwitch()
                        isSwitchBlocked.value = true
                    }
                    ProfileSwitchGuard.Result.Failed -> onSwitchFailed(profile)
                }
            }
        }
    }

    private suspend fun restartInto(profile: ProfileItem) {
        try {
            restartApp()
        } catch (e: Exception) {
            Timber.w(e, "Restarting into %s failed", profile.id)
            withContext(ioDispatcher) { profileManager.restoreRunningProfile() }
            onSwitchFailed(profile)
        }
    }

    private fun onSwitchFailed(profile: ProfileItem) {
        profileManager.endSwitch()
        profileSwitchFailed.value = profile
    }

    fun dismissSwitchBlockedMessage() {
        isSwitchBlocked.value = false
    }

    fun dismissSwitchFailedMessage() {
        profileSwitchFailed.value = null
    }

    fun deleteProfile(profile: ProfileItem) {
        Timber.i("Delete profile requested: %s", profile.id)
        // TODO: implement profile deletion via ProfileManager
    }

    /** Reloads the list from the registry. */
    fun refresh() {
        profiles.value = profileManager.profileItems()
    }

    companion object {
        fun factory(profileManager: ProfileManager): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SwitchProfilesViewModel(
                        profileManager = profileManager,
                        switchGuard = ProfileSwitchGuard(profileManager, listOf(syncCheck(), mediaSyncCheck(), backupCheck())),
                        restartApp = { safeRestartApp(appContext) },
                    )
                }
            }

        /**
         * The registry is backed by SharedPreferences, which has no defined order,
         * so sort by name to keep the list stable across launches.
         */
        private fun ProfileManager.profileItems(): List<ProfileItem> =
            getAllProfiles()
                .map { (id, metadata) -> ProfileItem(id = id, name = metadata.displayName.value) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
