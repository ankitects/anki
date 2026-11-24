// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Intent
import com.ichi2.anki.SingleFragmentScreenshotTest

/**
 * Screenshot tests for [CsvImporter]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.pages.CsvImporterScreenshotTest"`
 */
class CsvImporterScreenshotTest : SingleFragmentScreenshotTest() {
    override fun buildIntent(): Intent = CsvImporter.getIntent(targetContext, "/storage/emulated/0/notes.csv")
}
