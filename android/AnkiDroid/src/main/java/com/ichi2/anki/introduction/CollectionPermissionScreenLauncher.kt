/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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
