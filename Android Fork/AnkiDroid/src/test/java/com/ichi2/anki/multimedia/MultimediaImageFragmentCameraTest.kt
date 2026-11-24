// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.EXTRA_MEDIA_OPTIONS
import com.ichi2.anki.multimedia.MultimediaUtils.createImageFile
import com.ichi2.testutils.AnkiFragmentScenario
import com.ichi2.testutils.ShadowFileProviderPathStrategy
import com.ichi2.testutils.launchFragmentInContainer
import com.ichi2.testutils.withFragment
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowFileProviderPathStrategy::class])
class MultimediaImageFragmentCameraTest : RobolectricTest(loadBackend = false) {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `cancelling a replacement photo after recreation restores the original size`() =
        withCameraScenario { scenario ->
            val original = scenario.withFragment { completePhotoCapture("original") }
            val replacementRequest =
                scenario.withFragment {
                    assertThat(viewModel.selectedMediaFileSize, equalTo(original.length()))

                    composeRule.onNodeWithText(getString(CommonString.dialog_positive_replace)).performClick()
                    assertThat(viewModel.currentMultimediaPath.value!!.length(), equalTo(0L))
                    shadowOf(requireActivity()).nextStartedActivityForResult.requestCode
                }

            scenario.recreate()
            scenario.withFragment {
                deliverCameraResult(replacementRequest, Activity.RESULT_CANCELED)
                assertThat(viewModel.currentMultimediaPath.value, equalTo(original))
                assertThat(viewModel.selectedMediaFileSize, equalTo(original.length()))
                assertThat(binding.imageFileSize.text.toString(), equalTo(original.toHumanReadableSize()))
                assertThat(original.readText(), equalTo("original"))
            }
        }

    private fun MultimediaImageFragment.completePhotoCapture(contents: String): File {
        val capture = shadowOf(requireActivity()).nextStartedActivityForResult
        val image = requireNotNull(viewModel.currentMultimediaPath.value).apply { writeText(contents) }
        deliverCameraResult(capture.requestCode, Activity.RESULT_OK)
        ShadowDialog.getLatestDialog().dismiss()
        return image
    }

    private fun MultimediaImageFragment.deliverCameraResult(
        requestCode: Int,
        resultCode: Int,
    ) {
        requireActivity().activityResultRegistry.dispatchResult(requestCode, resultCode, null)
    }

    private fun withCameraScenario(block: (AnkiFragmentScenario<MultimediaImageFragment>) -> Unit) {
        clearFileProviderRoots()
        // Robolectric's external pictures directory is outside the FileProvider's simulated storage root.
        mockkObject(MultimediaUtils)
        try {
            every { any<Context>().createImageFile() } answers { File.createTempFile("camera", ".jpg", targetContext.cacheDir) }
            launchFragmentInContainer<MultimediaImageFragment>(
                Bundle().apply { putSerializable(EXTRA_MEDIA_OPTIONS, MultimediaImageFragment.ImageOptions.CAMERA) },
            ).use(block)
        } finally {
            unmockkObject(MultimediaUtils)
        }
    }
}
