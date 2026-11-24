// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.compose.theme.AnkiDroidTheme
import timber.log.Timber

/**
 * Lets the user switch between profiles.
 *
 * Thin host for the Compose [SwitchProfilesScreen]. Stays a Fragment because
 * preference_headers.xml launches it by class name.
 */
class SwitchProfilesFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val profileManager = AnkiDroidApp.instance.profileManager
        if (profileManager == null) {
            Timber.w("Profile environment unavailable, closing the switch profile screen")
            showThemedToast(requireContext(), CommonString.something_wrong, false)
            if (parentFragmentManager.backStackEntryCount > 0) {
                parentFragmentManager.popBackStack()
            } else {
                requireActivity().finish()
            }
            return View(requireContext())
        }
        val viewModel: SwitchProfilesViewModel by viewModels { SwitchProfilesViewModel.factory(profileManager) }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AnkiDroidTheme {
                    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
                    val isAddProfileDialogVisible by viewModel.isAddProfileDialogVisible.collectAsStateWithLifecycle()
                    val profileBeingRenamed by viewModel.profileBeingRenamed.collectAsStateWithLifecycle()
                    val profileToSwitchTo by viewModel.profileToSwitchTo.collectAsStateWithLifecycle()
                    val isSwitchBlocked by viewModel.isSwitchBlocked.collectAsStateWithLifecycle()
                    val profileSwitchFailed by viewModel.profileSwitchFailed.collectAsStateWithLifecycle()
                    // TODO: pass the user actions as one sealed class (MVI) instead of a callback each
                    SwitchProfilesScreen(
                        profiles = profiles,
                        isAddProfileDialogVisible = isAddProfileDialogVisible,
                        profileBeingRenamed = profileBeingRenamed,
                        profileToSwitchTo = profileToSwitchTo,
                        isSwitchBlocked = isSwitchBlocked,
                        profileSwitchFailed = profileSwitchFailed,
                        onNavigateUp = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                        onProfileClick = viewModel::requestSwitch,
                        onAddProfileClick = viewModel::showAddProfileDialog,
                        onAddProfileConfirm = viewModel::addProfile,
                        onAddProfileDismiss = viewModel::dismissAddProfileDialog,
                        onEditProfile = viewModel::showRenameProfileDialog,
                        onRenameProfileConfirm = viewModel::renameProfile,
                        onRenameProfileDismiss = viewModel::dismissRenameProfileDialog,
                        onSwitchProfileConfirm = viewModel::switchProfile,
                        onSwitchProfileDismiss = viewModel::dismissSwitchProfileDialog,
                        onSwitchBlockedDismiss = viewModel::dismissSwitchBlockedMessage,
                        onSwitchFailedDismiss = viewModel::dismissSwitchFailedMessage,
                        onDeleteProfile = viewModel::deleteProfile,
                    )
                }
            }
        }
    }
}
