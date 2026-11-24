// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Intent
import com.ichi2.anki.SingleFragmentScreenshotTest

/**
 * Screenshot tests for [AnkiPackageImporterFragment]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.pages.AnkiPackageImporterScreenshotTest"`
 */
class AnkiPackageImporterScreenshotTest : SingleFragmentScreenshotTest() {
    override fun buildIntent(): Intent = AnkiPackageImporterFragment.getIntent(targetContext, "/storage/emulated/0/collection.apkg")
}
