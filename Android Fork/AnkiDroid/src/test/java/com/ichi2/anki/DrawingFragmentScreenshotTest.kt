// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent

/**
 * Screenshot tests for [DrawingFragment]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.DrawingFragmentScreenshotTest"`
 */
class DrawingFragmentScreenshotTest : SingleFragmentScreenshotTest() {
    override fun buildIntent(): Intent = DrawingFragment.getIntent(targetContext)
}
