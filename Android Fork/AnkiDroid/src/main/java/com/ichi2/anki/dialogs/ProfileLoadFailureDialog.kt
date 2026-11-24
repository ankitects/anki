// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.dialogs

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.utils.ext.requireString
import com.ichi2.anki.utils.ext.setFragmentResultListener
import com.ichi2.utils.create
import com.ichi2.utils.message
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title
import timber.log.Timber

/** Tells the user their profile couldn't be opened, and offers to open Default from the next start. */
class ProfileLoadFailureDialog : AnalyticsDialogFragment() {
    private val profileName get() = ProfileName.fromTrustedSource(requireArguments().requireString(ARG_PROFILE_NAME))
    private val defaultProfileName get() = ProfileName.fromTrustedSource(requireArguments().requireString(ARG_DEFAULT_PROFILE_NAME))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProfileManager.LoadFailure(profileName, defaultProfileName) != ProfileManager.loadFailure) {
            Timber.w("Dismissing a restored profile load failure that no longer applies")
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog =
        AlertDialog.Builder(requireContext()).create {
            title(text = getString(CommonString.profile_load_failed_title, profileName.value))
            message(text = getString(CommonString.profile_load_failed_message, defaultProfileName.value, profileName.value))
            positiveButton(CommonString.dialog_ok) {
                Timber.i("Profile load failure: OK pressed")
                parentFragmentManager.setProfileLoadFailureClosedResult()
            }
            neutralButton(text = getString(CommonString.profile_load_failed_use_default, defaultProfileName.value))
        }

    override fun onStart() {
        super.onStart()
        (requireDialog() as AlertDialog).neutralButton?.setOnClickListener { useDefaultProfile() }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        Timber.i("Profile load failure: cancelled")
        parentFragmentManager.setProfileLoadFailureClosedResult()
    }

    private fun useDefaultProfile() {
        Timber.i("Profile load failure: Use Default pressed")
        if (!ProfileManager.selectDefaultProfile(requireContext().applicationContext)) {
            Timber.w("Could not save Default as the profile to open next")
            showThemedToast(requireContext(), getString(CommonString.something_wrong), shortLength = true)
            return
        }
        val manager = parentFragmentManager
        dismiss()
        manager.setProfileLoadFailureClosedResult()
    }

    companion object {
        const val TAG = "profileLoadFailure"
        const val REQUEST_PROFILE_LOAD_FAILURE_CLOSED = "request_profile_load_failure_closed"
        private const val ARG_PROFILE_NAME = "profileName"
        private const val ARG_DEFAULT_PROFILE_NAME = "defaultProfileName"

        fun newInstance(failure: ProfileManager.LoadFailure) =
            ProfileLoadFailureDialog().apply {
                arguments =
                    Bundle().apply {
                        putString(ARG_PROFILE_NAME, failure.profileName.value)
                        putString(ARG_DEFAULT_PROFILE_NAME, failure.defaultProfileName.value)
                    }
            }
    }
}

fun FragmentActivity.registerProfileLoadFailureClosedHandler(action: () -> Unit) {
    setFragmentResultListener(ProfileLoadFailureDialog.REQUEST_PROFILE_LOAD_FAILURE_CLOSED) { _, _ -> action() }
}

private fun FragmentManager.setProfileLoadFailureClosedResult() {
    setFragmentResult(ProfileLoadFailureDialog.REQUEST_PROFILE_LOAD_FAILURE_CLOSED, Bundle())
}
