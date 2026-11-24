// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multimedia

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.i18n.PreviewTranslations
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.utils.ext.getParcelableExtraCompat
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.EXTRA_MEDIA_OPTIONS
import com.ichi2.testutils.ShadowFileProviderPathStrategy
import com.ichi2.testutils.ShadowPreviewTranslations
import com.ichi2.testutils.launchFragmentInContainer
import com.ichi2.testutils.parcelledCopy
import com.ichi2.testutils.saveState
import com.ichi2.testutils.withFragment
import kotlinx.coroutines.job
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowActivity.IntentForResult
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals

@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowFileProviderPathStrategy::class, ShadowPreviewTranslations::class])
class MultimediaImageFragmentExternalEditorTest : RobolectricTest(loadBackend = false) {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val editedImage = editorResultImageBytes()
    private lateinit var resultProvider: EditorResultProvider

    @Before
    fun setUpEditor() {
        clearFileProviderRoots()
        resultProvider = EditorResultProvider.register()
    }

    @Test
    fun `edit is hidden without a compatible external editor`() =
        withImageFragment {
            selectImage()
            composeRule.onNodeWithText(PreviewTranslations.studyingEdit()).assertDoesNotExist()
        }

    @Test
    fun `edit availability follows the selected image type`() {
        targetContext.registerExternalImageEditor()
        withImageFragment {
            assertEditAvailable(false)
            selectImage()
            assertEditAvailable(true)
            selectImage(extension = "svg")
            assertEditAvailable(false)
            selectImage()
            assertEditAvailable(true)
        }
    }

    @Test
    fun `edit availability is refreshed on resume`() {
        imageScenario().use { scenario ->
            scenario.withFragment { selectImage() }
            assertEditAvailable(false)
            scenario.moveToState(Lifecycle.State.STARTED)
            val editor = targetContext.registerExternalImageEditor()
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEditAvailable(true)
            scenario.moveToState(Lifecycle.State.STARTED)
            targetContext.packageManager.setComponentEnabledSetting(
                editor,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEditAvailable(false)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a picked image without a filename extension can be edited`() = assertPickedPngCanBeEdited("extensionless")

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a picked image with an unrecognized filename extension can be edited`() = assertPickedPngCanBeEdited("image.bin")

    private fun assertPickedPngCanBeEdited(displayName: String) {
        targetContext.registerExternalImageEditor()
        withImageFragment {
            requireView().id = R.id.root_layout
            val gallery = shadowOf(requireActivity()).nextStartedActivityForResult.intent
            val source = File(requireContext().cacheDir, "provider-source.png")
            source.outputStream().use {
                createBitmap(1, 1).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val uri = resultProvider.result(source, displayName)
            shadowOf(requireActivity()).receiveResult(gallery, Activity.RESULT_OK, Intent().setData(uri))
            val selected = requireNotNull(viewModel.currentMultimediaPath.value)
            assertThat(selected.name, equalTo(displayName))
            assertThat(selected.readBytes().toList(), equalTo(source.readBytes().toList()))
            assertEditAvailable(true)
            val edit = launchExternalEditor()
            assertThat(edit.imageIntent.type, equalTo("image/png"))
            assertThat(requireContext().contentResolver.getType(edit.imageIntent.data!!), equalTo("image/png"))
        }
    }

    @Test
    fun `cancelling external editing preserves the original image`() =
        withImageFragment {
            val original = selectImage()
            val edit = launchExternalEditor()
            requireContext().contentResolver.openOutputStream(edit.imageIntent.data!!, "wt")!!.use { it.write(editedImage) }
            deliverEditorResult(edit, resultCode = Activity.RESULT_CANCELED)
            advanceRobolectricLooperUntil { !isExternalEditPending }
            assertThat(viewModel.currentMultimediaPath.value, equalTo(original))
            assertThat(original.readText(), equalTo("original"))
        }

    @Test
    fun `a failed import displays an error and preserves the original`() =
        withImageFragment {
            val original = selectImage()
            val edit = launchExternalEditor()
            requireContext().contentResolver.openOutputStream(edit.imageIntent.data!!, "wt")!!.close()
            deliverEditorResult(edit)
            assertOriginalPreserved(original, CommonString.multimedia_editor_something_wrong)
        }

    @Test
    @Config(sdk = [24, 25, 36])
    fun `external editing accepts an in-place result without result data`() =
        withImageFragment {
            val original = selectImage()
            val edit = launchExternalEditor()
            requireContext().contentResolver.openOutputStream(edit.imageIntent.data!!, "wt")!!.use { it.write(editedImage) }
            deliverEditorResult(edit)
            advanceRobolectricLooperUntil { !isExternalEditPending }
            assertContentEquals(editedImage, viewModel.currentMultimediaPath.value!!.readBytes())
            assertThat(viewModel.currentMultimediaPath.value, not(equalTo(original)))
            assertThat(viewModel.selectedMediaFileSize, equalTo(editedImage.size.toLong()))
            assertThat(original.readText(), equalTo("original"))
        }

    @Test
    @Config(sdk = [24, 25, 36])
    fun `external editing accepts a new content uri`() =
        withImageFragment {
            selectImage()
            val edit = launchExternalEditor()
            val result = File(requireContext().cacheDir, "edited.png").apply { writeBytes(editedImage) }
            val uri = resultProvider.result(result)
            deliverEditorResult(edit, uri = uri)
            advanceRobolectricLooperUntil { !isExternalEditPending }
            assertContentEquals(editedImage, viewModel.currentMultimediaPath.value!!.readBytes())
            assertThat(viewModel.selectedMediaFileSize, equalTo(result.length()))
        }

    @Test
    @Config(sdk = [24, 25, 36])
    fun `external editing survives fragment recreation`() {
        imageScenario().use { scenario ->
            val edit =
                scenario.withFragment {
                    selectImage()
                    launchExternalEditor().also {
                        requireContext().contentResolver.openOutputStream(it.imageIntent.data!!, "wt")!!.use { output ->
                            output.write(editedImage)
                        }
                    }
                }
            scenario.recreate()
            scenario.withFragment {
                deliverEditorResult(edit)
                advanceRobolectricLooperUntil { !isExternalEditPending }
                assertContentEquals(editedImage, viewModel.currentMultimediaPath.value!!.readBytes())
            }
            scenario.recreate()
            scenario.withFragment {
                assertContentEquals(editedImage, viewModel.currentMultimediaPath.value!!.readBytes())
            }
        }
    }

    @Test
    fun `external editing survives destruction of a stopped activity`() {
        val controller = startImageActivity()
        val activity = controller.get()
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as MultimediaImageFragment
        val original = fragment.selectImage()
        val edit = fragment.launchExternalEditor()
        val model = ViewModelProvider(fragment)[ExternalImageEditorViewModel::class.java]
        val working = File((model.state.value as ExternalImageEditorViewModel.Edit.Editing).workingPath)
        val savedState = controller.saveState().parcelledCopy(MultimediaActivity::class.java.classLoader)
        assertThat(activity.isFinishing, equalTo(false))
        assertThat(activity.isChangingConfigurations, equalTo(false))
        controller.destroy()
        advanceRobolectricLooperUntil { model.viewModelScope.coroutineContext.job.isCompleted }

        // "Don't keep activities" destroys the caller before the external editor finishes.
        assertThat(working.readText(), equalTo("original"))
        targetContext.contentResolver.openOutputStream(edit.imageIntent.data!!, "wt")!!.use { it.write(editedImage) }
        val restored =
            Robolectric
                .buildActivity(MultimediaActivity::class.java, activity.intent)
                .create(savedState)
                .start()
                .restoreInstanceState(savedState)
                .postCreate(savedState)
                .resume()
                .visible()
        saveControllerForCleanup(restored)
        val restoredFragment = restored.get().supportFragmentManager.findFragmentById(R.id.fragment_container) as MultimediaImageFragment
        with(restoredFragment) {
            deliverEditorResult(edit)
            advanceRobolectricLooperUntil { !isExternalEditPending }
            assertContentEquals(editedImage, viewModel.currentMultimediaPath.value!!.readBytes())
            assertThat(viewModel.selectedMediaFileSize, equalTo(editedImage.size.toLong()))
        }
        advanceRobolectricLooperUntil { !working.exists() }
        assertThat(original.readText(), equalTo("original"))
    }

    @Test
    fun `finishing the activity releases the external editor working copy`() {
        val controller = startImageActivity()
        val fragment = controller.get().supportFragmentManager.findFragmentById(R.id.fragment_container) as MultimediaImageFragment
        val original = fragment.selectImage()
        fragment.launchExternalEditor()
        controller.get().finish()
        controller.pause().stop().destroy()
        advanceRobolectricLooperUntil { File(targetContext.cacheDir, "temp-photos").listFiles()!!.isEmpty() }
        assertThat(original.readText(), equalTo("original"))
    }

    @Test
    fun `import continues across recreation while the provider is blocked`() =
        withBlockedImport { awaitImport, finishImport ->
            imageScenario().use { scenario ->
                scenario.withFragment {
                    selectImage()
                    val edit = launchExternalEditor()
                    val result = File(requireContext().cacheDir, "edited.png").apply { writeBytes(editedImage) }
                    deliverEditorResult(edit, uri = resultProvider.result(result))
                }
                awaitImport()
                scenario.recreate()
                scenario.withFragment {
                    assertThat(isExternalEditPending, equalTo(true))
                    composeRule.onNodeWithText(getString(CommonString.dialog_positive_replace)).assertIsNotEnabled()
                    composeRule.onNodeWithText(getString(CommonString.crop_button)).assertIsNotEnabled()
                    onDone() // A pending import must not finish with the original image.
                }
                finishImport()
                scenario.withFragment {
                    advanceRobolectricLooperUntil { !isExternalEditPending }
                    assertContentEquals(editedImage, viewModel.currentMultimediaPath.value!!.readBytes())
                }
            }
        }

    @Test
    fun `closing the screen during import cleans up temporary files`() =
        withBlockedImport { awaitImport, finishImport ->
            imageScenario().use { scenario ->
                scenario.withFragment {
                    selectImage()
                    val edit = launchExternalEditor()
                    val result = File(requireContext().cacheDir, "edited.png").apply { writeBytes(editedImage) }
                    deliverEditorResult(edit, uri = resultProvider.result(result))
                }
                awaitImport()
                scenario.moveToState(Lifecycle.State.DESTROYED)
                finishImport()
                advanceRobolectricLooperUntil {
                    File(targetContext.cacheDir, "temp-photos").listFiles()!!.isEmpty()
                }
                assertThat(File(targetContext.cacheDir, "original.png").readText(), equalTo("original"))
            }
        }

    @Test
    fun `done is disabled during external editing and import across recreation`() =
        withBlockedImport { awaitImport, finishImport ->
            val controller = startImageActivity()
            val fragment = controller.get().supportFragmentManager.findFragmentById(R.id.fragment_container) as MultimediaImageFragment
            val done = composeRule.onNodeWithText(targetContext.getString(CommonString.multimedia_editor_field_editing_done))
            done.assertIsEnabled()
            with(fragment) {
                selectImage()
                val edit = launchExternalEditor()
                done.assertIsNotEnabled()
                val result = File(requireContext().cacheDir, "edited.png").apply { writeBytes(editedImage) }
                deliverEditorResult(edit, uri = resultProvider.result(result))
            }
            awaitImport()
            controller.recreate()
            done.assertIsNotEnabled()
            finishImport()
            val restored = controller.get().supportFragmentManager.findFragmentById(R.id.fragment_container) as MultimediaImageFragment
            advanceRobolectricLooperUntil { !restored.isExternalEditPending }
            done.assertIsEnabled()
            with(restored) {
                val edit = launchExternalEditor()
                done.assertIsNotEnabled()
                deliverEditorResult(edit, resultCode = Activity.RESULT_CANCELED)
                advanceRobolectricLooperUntil { !isExternalEditPending }
            }
            done.assertIsEnabled()
        }

    @Test
    fun `unchanged in-place result preserves the original without offering selection`() =
        withImageFragment {
            val original = selectImage()
            val edit = launchExternalEditor()
            deliverEditorResult(edit)
            assertOriginalPreserved(original)
        }

    @Test
    fun `unchanged returned working uri preserves the original without offering selection`() =
        withImageFragment {
            val original = selectImage()
            val edit = launchExternalEditor()
            deliverEditorResult(edit, uri = edit.imageIntent.data)
            assertOriginalPreserved(original)
        }

    private fun MultimediaImageFragment.assertOriginalPreserved(
        original: File,
        @StringRes expectedMessage: Int = CommonString.external_image_editor_no_changes,
    ) {
        // Running all future looper tasks would dismiss the snackbar before we can inspect it.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            !isExternalEditPending
        }
        assertThat(viewModel.currentMultimediaPath.value, equalTo(original))
        assertThat(original.readText(), equalTo("original"))
        val message = requireActivity().findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        assertThat(message.text.toString(), equalTo(getString(expectedMessage)))
        val action = requireActivity().findViewById<TextView>(com.google.android.material.R.id.snackbar_action)
        assertThat(action.visibility, equalTo(View.GONE))
        advanceRobolectricLooperUntil { File(requireContext().cacheDir, "temp-photos").listFiles()!!.isEmpty() }
    }

    private fun MultimediaImageFragment.selectImage(extension: String = "png"): File {
        val file = File(requireContext().cacheDir, "original.$extension").apply { writeText("original") }
        viewModel.updateCurrentMultimediaPath(file)
        viewModel.updateCurrentMultimediaUri(Uri.fromFile(file))
        return file
    }

    private fun MultimediaImageFragment.launchExternalEditor(): IntentForResult {
        requireView().id = R.id.root_layout
        val activity = shadowOf(requireActivity())
        // Discard the gallery request made when the empty fragment starts.
        while (activity.nextStartedActivityForResult != null) { /* drain */ }
        advanceRobolectricLooper()
        requestExternalEdit()
        var launch: IntentForResult? = null
        advanceRobolectricLooperUntil {
            launch = activity.nextStartedActivityForResult
            launch != null
        }
        return requireNotNull(launch).also { assertThat(it.intent.action, equalTo(Intent.ACTION_CHOOSER)) }
    }

    private val IntentForResult.imageIntent: Intent
        get() = requireNotNull(intent.getParcelableExtraCompat<Intent>(Intent.EXTRA_INTENT))

    private fun MultimediaImageFragment.deliverEditorResult(
        edit: IntentForResult,
        uri: Uri? = null,
        resultCode: Int = Activity.RESULT_OK,
    ) {
        requireActivity().activityResultRegistry.dispatchResult(edit.requestCode, resultCode, uri?.let { Intent().setData(it) })
    }

    private fun assertEditAvailable(available: Boolean) {
        val label = PreviewTranslations.studyingEdit()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() == available
        }
        val edit = composeRule.onNodeWithText(label)
        if (available) edit.assertIsEnabled() else edit.assertDoesNotExist()
    }

    private fun withBlockedImport(block: (awaitImport: () -> Unit, finishImport: () -> Unit) -> Unit) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        resultProvider.beforeOpen = {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        }
        try {
            block(
                { assertThat("provider was opened on a worker", entered.await(10, TimeUnit.SECONDS), equalTo(true)) },
                { release.countDown() },
            )
        } finally {
            release.countDown()
        }
    }

    private fun imageScenario() =
        launchFragmentInContainer<MultimediaImageFragment>(
            Bundle().apply { putSerializable(EXTRA_MEDIA_OPTIONS, MultimediaImageFragment.ImageOptions.GALLERY) },
        )

    private fun startImageActivity() =
        startActivityControllerNormallyOpenCollectionWithIntent(
            MultimediaActivity::class.java,
            MultimediaActivity.getIntent(
                targetContext,
                MultimediaImageFragment::class,
                mediaOptions = MultimediaImageFragment.ImageOptions.GALLERY,
            ),
        )

    private fun withImageFragment(block: MultimediaImageFragment.() -> Unit) = imageScenario().use { it.withFragment(block) }
}
