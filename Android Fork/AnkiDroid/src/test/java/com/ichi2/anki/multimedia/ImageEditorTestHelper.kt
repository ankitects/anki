// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.RobolectricTest.Companion.advanceRobolectricLooper
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.multimediacard.impl.MultimediaEditableNote
import com.ichi2.imagecropper.ImageCropper
import org.junit.rules.ExternalResource
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.util.ReflectionHelpers
import java.io.File
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Launches the image editor and supplies results from its cropper and picker. */
class ImageEditorTestHelper(
    private val test: RobolectricTest,
    private val compose: SemanticsNodeInteractionsProvider,
) : ExternalResource() {
    private val context get() = test.targetContext
    private lateinit var controller: ActivityController<MultimediaActivity>

    val fragment: MultimediaImageFragment
        get() = controller.get().supportFragmentManager.findFragmentById(R.id.fragment_container) as MultimediaImageFragment

    override fun before() = clearFileProviderRoots()

    override fun after() = clearFileProviderRoots()

    private fun clearFileProviderRoots() {
        // Each Robolectric test has its own cache directory; discard AndroidX's cached roots.
        ReflectionHelpers.getStaticField<MutableMap<String, Any>>(FileProvider::class.java, "sCache").clear()
    }

    fun open(uri: Uri) {
        val field = ImageField()
        val note =
            MultimediaEditableNote().apply {
                setNumFields(1)
                setField(0, field)
                freezeInitialFieldValues()
            }
        val intent =
            MultimediaImageFragment.getIntent(
                context,
                MultimediaActivityExtra(0, field, note, uri.toString()),
                MultimediaImageFragment.ImageOptions.GALLERY,
            )
        controller = test.startActivityControllerNormallyOpenCollectionWithIntent(MultimediaActivity::class.java, intent)
    }

    fun cropTo(image: File) {
        val result =
            Intent().putExtra(
                ImageCropper.EXTRA_CROP_IMAGE_RESULT,
                ImageCropper.CropResultData(contentUri(image), image.absolutePath),
            )
        returnImage(CommonString.crop_button, result)
    }

    fun replaceWith(image: File) = returnImage(CommonString.dialog_positive_replace, Intent().setData(contentUri(image)))

    private fun returnImage(
        @StringRes button: Int,
        result: Intent,
    ) {
        compose.onNodeWithText(context.getString(button)).performClick()
        val activity = shadowOf(controller.get())
        val request = assertNotNull(activity.nextStartedActivityForResult)
        activity.receiveResult(request.intent, Activity.RESULT_OK, result)
        advanceRobolectricLooper()
    }

    fun recreate() {
        controller.recreate()
        advanceRobolectricLooper()
    }

    fun finish(): File {
        fragment.onDone()
        val activity = shadowOf(controller.get())
        val result = assertIs<MultimediaResult.Success>(MultimediaResultContract().parseResult(activity.resultCode, activity.resultIntent))
        return assertNotNull(result.field.mediaFile)
    }

    fun createImage(
        name: String,
        color: Int,
    ): File {
        val image = File(context.cacheDir, name)
        val bitmap = createBitmap(8, 8)
        bitmap.eraseColor(color)
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return image
    }

    fun contentUri(file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".apkgfileprovider", file)
}
