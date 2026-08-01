//noinspection MissingCopyrightHeader #8659

package com.ichi2.utils

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.utils.ClipboardUtil.AUDIO_MIME_TYPES
import com.ichi2.utils.ClipboardUtil.IMAGE_MIME_TYPES
import com.ichi2.utils.ClipboardUtil.VIDEO_MIME_TYPES
import com.ichi2.utils.ClipboardUtil.hasImage
import com.ichi2.utils.ClipboardUtil.hasMedia
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardUtilTest {
    private lateinit var clipboardManager: ClipboardManager

    @Before
    fun setUp() {
        clipboardManager =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    @Test
    fun hasImageClipboardManagerNullTest() {
        val clipboardManager: ClipboardManager? = null
        assertFalse(hasImage(clipboardManager))
    }

    @Test
    fun hasImageDescriptionNullTest() {
        val clipDescription: ClipDescription? = null
        assertFalse(hasImage(clipDescription))
    }

    @Test
    fun hasMediaClipboardManagerNullTest() {
        val clipboardManager: ClipboardManager? = null
        assertFalse(hasMedia(clipboardManager))
    }

    @Test
    fun hasMediaDescriptionNullTest() {
        val clipDescription: ClipDescription? = null
        assertFalse(hasMedia(clipDescription))
    }

    @Test
    fun hasMediaWithImageMimeTypeTest() {
        val clipDescription = ClipDescription("label", IMAGE_MIME_TYPES)
        val clipData = ClipData(clipDescription, ClipData.Item("image data"))
        clipboardManager.setPrimaryClip(clipData)
        assertTrue(hasMedia(clipboardManager))
    }

    @Test
    fun hasMediaWithSVGMimeTypeTest() {
        val clipDescription = ClipDescription("label", arrayOf("image/svg+xml"))
        val clipData = ClipData(clipDescription, ClipData.Item("svg data"))
        clipboardManager.setPrimaryClip(clipData)
        assertTrue(hasMedia(clipboardManager))
    }

    @Test
    fun hasMediaWithAudioMimeTypeTest() {
        val clipDescription = ClipDescription("label", AUDIO_MIME_TYPES)
        val clipData = ClipData(clipDescription, ClipData.Item("audio data"))
        clipboardManager.setPrimaryClip(clipData)
        assertTrue(hasMedia(clipboardManager))
    }

    @Test
    fun hasMediaWithVideoMimeTypeTest() {
        val clipDescription = ClipDescription("label", VIDEO_MIME_TYPES)
        val clipData = ClipData(clipDescription, ClipData.Item("video data"))
        clipboardManager.setPrimaryClip(clipData)
        assertTrue(hasMedia(clipboardManager))
    }
}
