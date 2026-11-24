// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.testutil.AnrTestActivity
import com.ichi2.anki.testutil.captureAnrTrace
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Generates a real ANR to check the running OS's trace format. */
@RunWith(AndroidJUnit4::class)
@LargeTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
class AnrTraceIntegrationTest {
    @Test
    fun decodesMainThreadFromNewPlatformAnr() {
        val frames = captureAnrTrace().reader().use { it.readAnrMainThreadStackTrace() }

        assertNotNull(frames, "Cannot decode the Android ${Build.VERSION.SDK_INT} ANR format")
        assertTrue(
            frames.any { it.className == AnrTestActivity::class.java.name && it.methodName == "deliberatelyBlockMainThread" },
            "Decoded trace must contain the deliberate main-thread block: ${frames.contentToString()}",
        )
    }
}
