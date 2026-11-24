// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.anki.compat.GET_ACTIVITIES_L
import com.ichi2.anki.compat.PackageInfoFlagsCompat
import com.ichi2.testutils.ActivityList
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class ActivityStartupMetaTest : AndroidTest {
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
                .filter { it != "com.jakewharton.processphoenix.PhoenixActivity" }
                .toTypedArray()
        MatcherAssert.assertThat(testedActivityClassNames, Matchers.containsInAnyOrder(*manifestActivityNames))
    }
}
