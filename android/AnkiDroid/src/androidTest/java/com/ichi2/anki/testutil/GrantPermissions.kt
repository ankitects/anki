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

package com.ichi2.anki.testutil

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.ichi2.anki.utils.ensureAllFilesAccess
import org.junit.rules.TestRule

object GrantStoragePermission {
    private val targetSdkVersion =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext.applicationInfo.targetSdkVersion
    val storagePermission =
        if (
            targetSdkVersion >= Build.VERSION_CODES.R &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        ) {
            null
        } else {
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        }

    /**
     * Storage is longer necessary for API 30+
     * This specific rule is very common, so use a flyweight
     */
    val instance: TestRule = grantPermissions(storagePermission)
}

val notificationPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }

/** Grants permissions, given some may be invalid */
fun grantPermissions(vararg permissions: String?): TestRule {
    val validPermissions = permissions.filterNotNull().toTypedArray()
    ensureAllFilesAccess()
    return GrantPermissionRule.grant(*validPermissions)
}
