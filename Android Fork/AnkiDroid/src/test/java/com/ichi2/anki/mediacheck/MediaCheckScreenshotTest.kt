// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.mediacheck

import android.content.Intent
import com.ichi2.anki.SingleFragmentScreenshotTest

/**
 * Screenshot tests for [MediaCheckFragment]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.mediacheck.MediaCheckScreenshotTest"`
 */
class MediaCheckScreenshotTest : SingleFragmentScreenshotTest() {
    // Backend media checks require an on-disk collection and media database.
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    override fun buildIntent(): Intent = MediaCheckFragment.getIntent(targetContext)
}
