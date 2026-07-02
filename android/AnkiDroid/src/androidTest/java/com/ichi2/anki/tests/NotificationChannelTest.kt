/*
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.tests

import android.app.NotificationManager
import android.os.Build
import androidx.core.content.getSystemService
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.Channel
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.compat.CompatHelper.Companion.sdkVersion
import com.ichi2.anki.testutil.GrantStoragePermission
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import kotlin.test.junit.JUnitAsserter.assertNotNull

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O) // getNotificationChannels, NotificationChannel.getId

@KotlinCleanup("Enable JUnit 5 in androidTest and use JUnit5Asserter to match the standard tests")
class NotificationChannelTest : InstrumentedTest() {
    @get:Rule
    var runtimePermissionRule = GrantStoragePermission.instance
    private var currentAPI = -1
    private var targetAPI = -1

    private lateinit var manager: NotificationManager

    @Before
    @UiThreadTest
    fun setUp() {
        val targetContext = testContext
        (targetContext.applicationContext as AnkiDroidApp).onCreate()
        currentAPI = sdkVersion
        targetAPI = targetContext.applicationInfo.targetSdkVersion
        manager = targetContext.getSystemService<NotificationManager>()!!
    }

    private fun channelsInAPI(): Boolean = currentAPI >= 26

    @Test
    fun testChannelCreation() {
        if (!channelsInAPI()) return

        // onCreate was called in setUp(), so we should have channels now
        val channels = manager.notificationChannels
        for (i in channels.indices) {
            Timber.d("Found channel with id %s", channels[i].id)
        }
        var expectedChannels = Channel.entries.size
        // If we have channels but have *targeted* pre-26, there is a "miscellaneous" channel auto-defined
        if (targetAPI < 26) {
            expectedChannels += 1
        }

        // Any channels we see that are for "LeakCanary" are okay. They are auto-created on test devices.
        for (channel in channels) {
            if (channel.name.toString().contains("LeakCanary")) {
                expectedChannels += 1
            }
        }
        assertThat(
            "Not as many channels as expected.",
            expectedChannels,
            greaterThanOrEqualTo(channels.size),
        )
        for (channel in Channel.entries) {
            assertNotNull(
                "There should be a reminder channel",
                manager.getNotificationChannel(channel.id),
            )
        }
    }
}
