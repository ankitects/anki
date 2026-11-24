// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.setIntroductionSlidesShown
import com.ichi2.anki.settings.Prefs
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowToast
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

@RunWith(AndroidJUnit4::class)
class DeckPickerBackgroundTest : RobolectricTest() {
    @Test
    fun `background is released while the deck picker is stopped`() {
        saveBackgroundImage(width = 10, height = 10)
        val controller = startDeckPicker()
        val background = controller.get().deckPickerBinding.background
        advanceRobolectricLooperUntil { background.drawable != null }

        controller.pause().stop()
        assertThat(background.drawable, nullValue())

        controller.start().resume()
        advanceRobolectricLooperUntil { background.drawable != null }
    }

    @Test
    fun `a background that is too large is reported once`() {
        saveBackgroundImage(width = 6_000, height = 5_000)
        val controller = startDeckPicker()
        advanceRobolectricLooperUntil { ShadowToast.shownToastCount() > 0 }

        controller.pause().stop()
        controller.start().resume()
        advanceRobolectricLooper()

        assertThat(ShadowToast.shownToastCount(), equalTo(1))
        assertThat(ShadowToast.getTextOfLatestToast(), equalTo(targetContext.getString(CommonString.background_image_too_large)))
    }

    private fun saveBackgroundImage(
        width: Int,
        height: Int,
    ) {
        val directory = CollectionHelper.getCurrentAnkiDroidDirectory(targetContext).apply { mkdirs() }
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY), "png", File(directory, BackgroundImage.FILENAME))
        Prefs.isBackgroundEnabled = true
    }

    private fun startDeckPicker(): ActivityController<DeckPicker> {
        setIntroductionSlidesShown(true)
        return startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
    }
}
