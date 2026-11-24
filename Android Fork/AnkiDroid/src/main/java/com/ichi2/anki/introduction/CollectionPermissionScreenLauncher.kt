// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.introduction

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.selectStoragePermissions
import com.ichi2.anki.ui.windows.permissions.PermissionsActivity
import timber.log.Timber

/**
 * Launcher for [PermissionsActivity]
 * @see collectionPermissionScreenWasOpened
 */
interface CollectionPermissionScreenLauncher {
    // we can't use get() as registerForActivityResult MUST be called unconditionally
    val permissionScreenLauncher: ActivityResultLauncher<Intent>

    /** An [ActivityResultLauncher] which recreates the activity in the callback */
    fun AnkiActivity.recreateActivityResultLauncher() =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            ActivityCompat.recreate(this)
        }

    /**
     * If required, opens [PermissionsActivity] to grant storage permissions
     * @return `true` if the screen was opened
     */
    fun AnkiActivity.collectionPermissionScreenWasOpened(): Boolean {
        val permissions = selectStoragePermissions(this)
        if (!permissions.hasRequiredPermissions(this)) {
            Timber.i("${this.javaClass.simpleName}: postponing startup code - permission screen shown")
            permissionScreenLauncher.launch(PermissionsActivity.getIntent(this, permissions))
            return true
        }
        return false
    }
}

fun AnkiActivity.hasCollectionStoragePermissions(): Boolean {
    val permissions = selectStoragePermissions(this)
    return permissions.hasRequiredPermissions(this)
}
