// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Intent
import com.ichi2.anki.SingleFragmentScreenshotTest
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.pages.viewmodel.ImageOcclusionArgs

/**
 * Screenshot tests for [ImageOcclusion]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.pages.ImageOcclusionScreenshotTest"`
 */
class ImageOcclusionScreenshotTest : SingleFragmentScreenshotTest() {
    override fun buildIntent(): Intent {
        val noteTypeId = col.notetypes.imageOcclusion.id
        return ImageOcclusion.getIntent(
            targetContext,
            ImageOcclusionArgs.Add(
                imagePath = "/storage/emulated/0/image.jpg",
                noteTypeId = noteTypeId,
                originalDeckId = Consts.DEFAULT_DECK_ID,
            ),
        )
    }
}
