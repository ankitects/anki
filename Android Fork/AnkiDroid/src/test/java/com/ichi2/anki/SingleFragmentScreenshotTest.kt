// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import com.ichi2.anki.common.utils.dp
import com.ichi2.testutils.simulateSystemBars
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/**
 * Base class for screenshot tests of a fragment hosted in [SingleFragmentActivity].
 *
 * Subclasses provide the [Intent] which launches the screen via [buildIntent].
 */
abstract class SingleFragmentScreenshotTest : ScreenshotTest() {
    /** The intent used to launch the screen under test */
    protected abstract fun buildIntent(): Intent

    @Test
    fun default() = withScreen { captureScreen("default") }

    @Test
    fun `system bars`() =
        withScreen { activity ->
            activity.simulateSystemBars()
            captureScreen("systemBars")
        }

    @Test
    fun `landscape display cutout`() {
        RuntimeEnvironment.setQualifiers("+land")
        withScreen { activity ->
            activity.simulateSystemBars(cutoutLeft = 32.dp)
            captureScreen("landscapeCutout")
        }
    }

    protected open fun withScreen(block: (SingleFragmentActivity) -> Unit) {
        val activity =
            startActivityNormallyOpenCollectionWithIntent(
                SingleFragmentActivity::class.java,
                buildIntent(),
            )
        advanceRobolectricLooper()
        block(activity)
    }
}
