// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.app.Activity
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.hasSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import com.ichi2.testutils.Robolectric as RobolectricActivities

@RunWith(AndroidJUnit4::class)
class RobolectricLiveActivitiesTest : RobolectricTest() {
    @Test
    fun `liveActivities empty after ActivityController close`() {
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup()
            assertThat(RobolectricActivities.liveActivities(), hasSize(1))
        }
        assertThat(RobolectricActivities.liveActivities(), empty())
    }

    @Test
    fun `assertNoLiveActivities fails and clears leftovers`() {
        Robolectric.buildActivity(Activity::class.java).setup()
        assertThat(RobolectricActivities.liveActivities(), hasSize(1))

        val error =
            assertFailsWith<AssertionError> {
                RobolectricActivities.assertNoLiveActivities("unit test")
            }
        assertThat(error.message!!.contains("leftover activities"), org.hamcrest.Matchers.equalTo(true))
        assertThat(RobolectricActivities.liveActivities(), empty())
    }

    @Test
    fun `cleanup disposes a delegate even when onCreate never completed`() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        val controller = Robolectric.buildActivity(ThrowingCreateActivity::class.java)
        saveControllerForCleanup(controller)
        assertFailsWith<IllegalStateException> { controller.create() }

        RobolectricActivities.closeActivity(controller)

        assertThat(RobolectricActivities.liveActivities(), empty())
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, controller.get().recreationCount)
    }

    @Test
    fun `owned controller can already be closed before teardown`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        saveControllerForCleanup(controller)
        controller.close()
    }

    class ThrowingCreateActivity : AppCompatActivity() {
        var recreationCount = 0

        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(androidx.appcompat.R.style.Theme_AppCompat)
            super.onCreate(savedInstanceState)
            error("activity creation failed after registering its delegate")
        }

        override fun recreate() {
            recreationCount++
            super.recreate()
        }
    }
}
