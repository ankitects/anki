// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.imagecropper

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.graphics.createBitmap
import com.ichi2.anki.SingleFragmentActivity
import com.ichi2.anki.SingleFragmentScreenshotTest
import com.ichi2.anki.databinding.FragmentImageCropperBinding
import org.junit.Before
import java.io.File

/**
 * Screenshot tests for [ImageCropper]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.imagecropper.ImageCropperScreenshotTest"`
 */
class ImageCropperScreenshotTest : SingleFragmentScreenshotTest() {
    override fun buildIntent(): Intent = ImageCropperLauncher.ImageUri(createImage()).getIntent(targetContext)

    @Before
    fun setMaxTextureSize() {
        // OpenGL classes are unavailable under Robolectric; set the cache to a reasonable value
        BitmapUtils.maxTextureSize = 2048
    }

    override fun withScreen(block: (SingleFragmentActivity) -> Unit) {
        super.withScreen { activity ->
            activity.waitForImageToLoad()
            block(activity)
        }
    }

    /** The image is decoded on a background thread; wait for it to be displayed */
    private fun SingleFragmentActivity.waitForImageToLoad() {
        val binding = FragmentImageCropperBinding.bind(fragment!!.requireView())
        advanceRobolectricLooperUntil { binding.cropImageView.wholeImageRect != null }
    }

    /** A solid-color image, large enough for the default crop window */
    private fun createImage(): Uri {
        val file = File(targetContext.cacheDir, "image_cropper_test.png")
        val bitmap = createBitmap(600, 1400)
        bitmap.eraseColor(Color.rgb(100, 149, 237))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }
}

/**
 * Reflection-based access to `com.canhub.cropper.BitmapUtils`, which is `internal` to the library
 */
private object BitmapUtils {
    private val clazz = Class.forName("com.canhub.cropper.BitmapUtils")
    private val instance = clazz.getDeclaredField("INSTANCE").get(null)
    private val maxTextureSizeField = clazz.getDeclaredField("mMaxTextureSize").apply { isAccessible = true }

    /** The cached maximum OpenGL texture size; queried from OpenGL when `0` */
    var maxTextureSize: Int
        get() = maxTextureSizeField.getInt(instance)
        set(value) = maxTextureSizeField.setInt(instance, value)
}
