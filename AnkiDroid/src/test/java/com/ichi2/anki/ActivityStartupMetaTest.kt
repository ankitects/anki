/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.anki.compat.GET_ACTIVITIES_L
import com.ichi2.anki.compat.PackageInfoFlagsCompat
import com.ichi2.testutils.ActivityList
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityStartupMetaTest : RobolectricTest() {
    @Test
    fun ensureAllActivitiesAreTested() {
        // if this fails, you may need to add the missing activity to ActivityList.allActivitiesAndIntents()

        // we can't access this in a static context
        val flags = PackageInfoFlagsCompat.of(GET_ACTIVITIES_L)
        val packageInfo =
            targetContext.getPackageInfoCompat(targetContext.packageName, flags)
                ?: throw IllegalStateException("getPackageInfo failed")
        val manifestActivities = packageInfo.activities ?: throw IllegalStateException("activity list")
        val testedActivityClassNames = ActivityList.allActivitiesAndIntents().map { it.className }.toSet()
        val manifestActivityNames =
            manifestActivities
                .map { it.name }
                .filter { it != "com.ichi2.anki.TestCardTemplatePreviewer" }
                // activity aliases
                .filter { it != "com.ichi2.anki.AnkiCardContextMenuAction" }
                .filter { it != "com.ichi2.anki.CardBrowserDeepLink" }
                // ACRA-specific
                .filter { it != "com.ichi2.anki.analytics.AnkiDroidCrashReportDialog" }
                .filter { !it.startsWith("androidx") }
                .filter { !it.startsWith("org.acra") }
                .filter { !it.startsWith("leakcanary.internal") }
                .filter { it != "com.canhub.cropper.CropImageActivity" }
                .toTypedArray()
        MatcherAssert.assertThat(testedActivityClassNames, Matchers.containsInAnyOrder(*manifestActivityNames))
    }
}
