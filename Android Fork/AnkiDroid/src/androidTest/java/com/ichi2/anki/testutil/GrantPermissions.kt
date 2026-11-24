// SPDX-License-Identifier: GPL-3.0-or-later

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
