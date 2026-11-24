// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.graphics.Color
import android.net.Uri
import android.text.format.Formatter
import android.webkit.WebView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.ShadowFileProviderPathStrategy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowFileProviderPathStrategy::class])
class MultimediaImageRecreationTest : RobolectricTest(loadBackend = false) {
    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val editor = ImageEditorTestHelper(this, composeRule)

    @Test
    fun `cropped gallery image survives recreation`() {
        val original = editor.createImage("original.png", Color.RED)
        assertCropSurvivesRecreation(editor.contentUri(original))
    }

    @Test
    fun `cropped shared image survives recreation`() {
        val original = editor.createImage("original.png", Color.RED)
        assertCropSurvivesRecreation(Uri.fromFile(original))
    }

    @Test
    fun `replacement image survives recreation`() {
        editor.open(editor.contentUri(editor.createImage("original.png", Color.RED)))
        val replacement = editor.createImage("replacement.png", Color.BLUE)
        editor.replaceWith(replacement)

        assertEditSurvivesRecreation(replacement)
    }

    private fun assertCropSurvivesRecreation(original: Uri) {
        editor.open(original)
        val cropped = editor.createImage("cropped.png", Color.GREEN)
        editor.cropTo(cropped)

        assertEditSurvivesRecreation(cropped)
    }

    private fun assertEditSurvivesRecreation(expectedImage: File) {
        val editedFile = assertNotNull(editor.fragment.viewModel.currentMultimediaPath.value)
        val expectedUri = editor.contentUri(expectedImage)
        assertEquals(expectedUri, editor.fragment.viewModel.currentMultimediaUri.value)

        editor.recreate()

        val fragment = editor.fragment
        assertEquals(editedFile, fragment.viewModel.currentMultimediaPath.value)
        assertEquals(expectedUri, fragment.viewModel.currentMultimediaUri.value)
        assertEquals(expectedImage.length(), fragment.viewModel.selectedMediaFileSize)
        assertEquals(
            Formatter.formatFileSize(targetContext, expectedImage.length()),
            fragment.binding.imageFileSize.text
                .toString(),
        )
        val webView = fragment.binding.multimediaWebView.getChildAt(0) as WebView
        assertContains(shadowOf(webView).lastLoadDataWithBaseURL.data, editedFile.name)
        assertNull(shadowOf(fragment.requireActivity()).nextStartedActivityForResult)

        val result = editor.finish()
        assertEquals(editedFile, result)
        assertContentEquals(expectedImage.readBytes(), result.readBytes())
    }
}
