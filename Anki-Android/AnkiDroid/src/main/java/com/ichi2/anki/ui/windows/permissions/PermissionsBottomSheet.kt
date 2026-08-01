/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.ui.windows.permissions

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.PermissionSet
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentPermissionsBottomSheetBinding
import com.ichi2.anki.utils.ext.behavior
import com.ichi2.anki.utils.ext.requireParcelable
import dev.androidbroadcast.vbpd.viewBinding

/**
 * BottomSheet that requests permissions from the user.
 *
 * The full-screen [PermissionsActivity] which launches on initial app installation should be used to request
 * mandatory permissions from the user that AnkiDroid cannot run without. This more relaxed BottomSheet
 * should be used to request optional permissions from the user, and can be launched as the user gradually
 * encounters features that require permissions rather than being shoved in the face of every first-time user.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class PermissionsBottomSheet : BottomSheetDialogFragment() {
    private val binding by viewBinding(FragmentPermissionsBottomSheetBinding::bind)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_permissions_bottom_sheet, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        this.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        binding.closeButton.setOnClickListener { dismiss() }
        childFragmentManager.setFragmentResultListener(RESULT_DISMISS, this) { _, _ ->
            dismiss()
        }

        val permissionSet = requireArguments().requireParcelable<PermissionSet>(ARG_PERMISSION_SET)
        val permissionsFragment =
            requireNotNull(permissionSet.permissionsFragment?.getDeclaredConstructor()?.newInstance()) {
                "invalid permissionsFragment"
            }
        view.post {
            childFragmentManager.commit {
                replace(binding.bottomSheetFragmentContainer.id, permissionsFragment)
            }
        }
    }

    companion object {
        /**
         * Unique fragment tag for launching this bottom sheet.
         */
        private const val FRAGMENT_TAG = "notifications_bottom_sheet"

        /**
         * Arguments key for the [PermissionSet] to launch this BottomSheet with.
         */
        private const val ARG_PERMISSION_SET = "arg_permission_set"

        /**
         * Fragment result request key for dismissing this BottomSheet.
         * Public so that child fragments can set it.
         */
        const val RESULT_DISMISS = "result_dismiss"

        /**
         * Starts this BottomSheet with the provided [PermissionSet].
         */
        fun launch(
            fragmentManager: FragmentManager,
            permissionsSet: PermissionSet,
        ) {
            val bottomSheet =
                PermissionsBottomSheet().apply {
                    arguments = Bundle().apply { putParcelable(ARG_PERMISSION_SET, permissionsSet) }
                }
            bottomSheet.show(fragmentManager, FRAGMENT_TAG)
        }
    }
}
