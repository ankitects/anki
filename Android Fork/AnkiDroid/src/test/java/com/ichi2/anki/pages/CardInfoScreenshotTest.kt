// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Intent
import com.ichi2.anki.SingleFragmentScreenshotTest
import com.ichi2.anki.common.destinations.CardInfoDestination

/**
 * Screenshot tests for [CardInfoFragment]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.pages.CardInfoScreenshotTest"`
 */
class CardInfoScreenshotTest : SingleFragmentScreenshotTest() {
    override fun buildIntent(): Intent {
        val cardId = addBasicNote().firstCard().id
        return CardInfoDestination(cardId, CardInfoDestination.EntryPoint.CURRENT_CARD_STUDY).toIntent(targetContext)
    }
}
