// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import android.os.Build
import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.utils.Permissions
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class EnsureAllFilesAccessRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement {
        ensureAllFilesAccess()
        return base
    }
}

fun ensureAllFilesAccess() {
    // PERF: Could be sped up - only need to calculate this once.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        Permissions.canManageExternalStorage(InstrumentationRegistry.getInstrumentation().targetContext) &&
        !Environment.isExternalStorageManager() &&
        !Environment.isExternalStorageLegacy()
    ) {
        // TODO: https://stackoverflow.com/q/75102412 to grant access, but see if we can remove dependency
        throw IllegalStateException(
            "'All Files' access is required on your emulator/device. " +
                "Please grant it manually or change Build Variant to 'playDebug' in Android Studio " +
                "(Build -> Select Build Variant)",
        )
    }
}
