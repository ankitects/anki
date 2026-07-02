/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.testutils

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

/**
 * Methods for interacting with Robolectric
 */
object Robolectric {
    /**
     * Allows a test activity to be launched under Robolectric
     * This is unusually difficult due to AGP not merging test manifests
     */
    inline fun <reified TestActivity : Activity> registerTestActivity() {
        // https://github.com/robolectric/robolectric/pull/4736
        val context: Context = ApplicationProvider.getApplicationContext()
        val activityInfo =
            ActivityInfo().apply {
                name = TestActivity::class.java.name
                packageName = context.packageName
            }
        shadowOf(context.packageManager).addOrUpdateActivity(activityInfo)
    }
}
