// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Intent
import com.ichi2.anki.SingleFragmentScreenshotTest
import com.ichi2.anki.common.destinations.StatisticsDestination

/**
 * Screenshot tests for [Statistics]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.pages.StatisticsScreenshotTest"`
 */
class StatisticsScreenshotTest : SingleFragmentScreenshotTest() {
    override fun buildIntent(): Intent = StatisticsDestination.toIntent(targetContext)
}
