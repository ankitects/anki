// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.commit
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.InitialActivity
import com.ichi2.anki.R
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.ext.getParcelableExtraCompat
import com.ichi2.anki.databinding.ActivityPermissionsBinding
import com.ichi2.anki.ui.windows.permissions.PermissionsFragment.Companion.REQUEST_PERMISSIONS
import com.ichi2.anki.ui.windows.permissions.PermissionsFragment.Companion.RESULT_HAS_ALL_PERMISSIONS
import com.ichi2.anki.utils.ext.setFragmentResultListener
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

/**
 * Screen responsible for getting permissions from the user.
 *
 * Prefer using [PermissionsActivity.getIntent] to get an intent to this activity.
 *
 * Advantages:
 * * Explains why each permission should be granted
 * * Easily reusable
 * * Doesn't need to block any UI elements or background routines that depends on a permission.
 *     Nor needs to add callbacks after the permissions are granted
 *
 * To request optional permissions from the user, prefer [PermissionsBottomSheet].
 */
class PermissionsActivity : AnkiActivity(R.layout.activity_permissions) {
    internal val binding by viewBinding(ActivityPermissionsBinding::bind)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) {
            return
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setViewBinding(binding)

        binding.continueButton.setOnClickListener { decideStorageAndFinish() }

        // #20881: Activity should not be launchd without extras
        val permissionSet = intent.getParcelableExtraCompat<StoragePermissionSet>(EXTRA_PERMISSIONS_SET)
        if (permissionSet == null) {
            Timber.w("EXTRA_PERMISSIONS_SET not set; finishing")
            showThemedToast(this, CommonString.something_wrong, false)
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        configurePermissions(permissionSet)
        // only close the activity by tapping the continue button
        onBackPressedDispatcher.addCallback {}
    }

    private fun configurePermissions(permissionSet: StoragePermissionSet) {
        setFragmentResultListener(REQUEST_PERMISSIONS) { _, bundle ->
            val hasAllPermissions = bundle.getBoolean(RESULT_HAS_ALL_PERMISSIONS)
            setContinueButtonEnabled(hasAllPermissions)
        }
        setFragmentResultListener(REQUEST_PERMISSIONS_GRANTED) { _, _ -> decideStorageAndFinish() }

        showPermissionsFragment(permissionSet)
    }

    private fun showPermissionsFragment(permissionSet: StoragePermissionSet) {
        val permissionsFragmentClass =
            permissionSet.permissionsFragment ?: run {
                Timber.w("%s has no permissions to request", permissionSet)
                decideStorageAndFinish()
                return
            }
        val permissionsFragment = permissionsFragmentClass.getDeclaredConstructor().newInstance()
        supportFragmentManager.commit {
            replace(R.id.fragment_container, permissionsFragment)
        }
    }

    private fun setContinueButtonEnabled(isEnabled: Boolean) {
        binding.continueButton.isEnabled = isEnabled
    }

    /**
     * Records the storage decision and closes the screen: the granted permissions
     * determine the default collection path.
     *
     * Called on 'Continue', or when the fragment reports [REQUEST_PERMISSIONS_GRANTED].
     * Preserves an existing collection path.
     *
     * @see InitialActivity.decideStorageIfUndecided
     */
    private fun decideStorageAndFinish() {
        InitialActivity.decideStorageIfUndecided(this)
        finish()
    }

    companion object {
        const val EXTRA_PERMISSIONS_SET = "permissionsSet"

        /**
         * Fragment result request key: all the fragment's permissions are granted.
         *
         * Signals the activity to persist the
         * [storage decision][com.ichi2.anki.common.storage.StorageDecision], and then close.
         */
        const val REQUEST_PERMISSIONS_GRANTED = "result_complete"

        fun getIntent(
            context: Context,
            permissionsSet: StoragePermissionSet,
        ): Intent =
            Intent(context, PermissionsActivity::class.java).apply {
                putExtra(EXTRA_PERMISSIONS_SET, permissionsSet as Parcelable)
            }
    }
}
