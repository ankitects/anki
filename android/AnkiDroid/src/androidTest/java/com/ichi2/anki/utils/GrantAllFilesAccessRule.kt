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
