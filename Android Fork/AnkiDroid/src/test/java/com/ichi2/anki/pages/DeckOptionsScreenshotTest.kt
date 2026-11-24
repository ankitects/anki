// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Intent
import com.ichi2.anki.SingleFragmentScreenshotTest
import com.ichi2.anki.libanki.Consts

/**
 * Screenshot tests for [DeckOptions]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.pages.DeckOptionsScreenshotTest"`
 */
class DeckOptionsScreenshotTest : SingleFragmentScreenshotTest() {
    override fun buildIntent(): Intent = DeckOptions.getIntent(targetContext, Consts.DEFAULT_DECK_ID)
}
