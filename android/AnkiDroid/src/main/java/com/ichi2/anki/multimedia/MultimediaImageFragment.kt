/*
 * Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multimedia

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.DrawingFragment
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.databinding.FragmentMultimediaImageBinding
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.EXTRA_MEDIA_OPTIONS
import com.ichi2.anki.multimedia.MultimediaUtils.IMAGE_LIMIT
import com.ichi2.anki.multimedia.MultimediaUtils.IMAGE_SAVE_MAX_WIDTH
import com.ichi2.anki.multimedia.MultimediaUtils.createCachedFile
import com.ichi2.anki.multimedia.MultimediaUtils.createImageFile
import com.ichi2.anki.multimedia.MultimediaUtils.createNewCacheImageFile
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.convertToString
import com.ichi2.anki.utils.ext.toBase64Png
import com.ichi2.imagecropper.ImageCropper
import com.ichi2.imagecropper.ImageCropper.Companion.CROP_IMAGE_RESULT
import com.ichi2.utils.BitmapUtil
import com.ichi2.utils.ExifUtil
import com.ichi2.utils.FileUtil
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.openInputStreamSafe
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.NumberFormat

private const val SVG_IMAGE = "image/svg+xml"

@NeedsTest("Ensure correct option is executed i.e. gallery or camera")
class MultimediaImageFragment : MultimediaFragment(R.layout.fragment_multimedia_image) {
    private val binding by viewBinding(FragmentMultimediaImageBinding::bind)

    override val title: String
        get() = resources.getString(R.string.multimedia_editor_popup_image)

    private lateinit var selectedImageOptions: ImageOptions

    /** Keeps track of the process in case `Don't keep activities` in turned on*/
    private var hasStartedImageSelection = false

    /**
     * Launches an activity to pick an image from the device's gallery.
     * This launcher is registered using `ActivityResultContracts.StartActivityForResult()`.
     */
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            hasStartedImageSelection = false
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> {
                    cancelIfEmpty()
                }

                Activity.RESULT_OK -> {
                    val data = result.data
                    if (data == null) {
                        Timber.w("handleSelectImageIntent() no intent provided")
                        showSomethingWentWrong()
                        return@registerForActivityResult
                    }

                    val selectedImage = getImageUri(data)
                    handleSelectImageIntent(selectedImage)
                }
            }
        }

    /**
     * Launches the [DrawingFragment] and handles the result by adding the drawing as image.
     */
    private val drawingActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> {
                    // If user didn't draw, return the indexValue as a result and finish the activity
                    cancelIfEmpty()
                }

                Activity.RESULT_OK -> {
                    val intent = result.data ?: return@registerForActivityResult
                    Timber.d("Intent not null, handling the result")
                    handleDrawingResult(intent)
                }
            }
        }

    /**
     * Launches the device's camera to take a picture.
     * This launcher is registered using `ActivityResultContracts.TakePicture()`.
     */
    @NeedsTest("Works fine without permission as we use Camera as feature")
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isPictureTaken ->
            hasStartedImageSelection = false
            when {
                !isPictureTaken && viewModel.currentMultimediaUri.value == null -> {
                    cancelIfEmpty()
                }

                isPictureTaken -> {
                    Timber.d("Image successfully captured")
                    handleTakePictureResult(viewModel.currentMultimediaPath.value)
                }

                else -> {
                    Timber.d("Camera aborted or some interruption, restoring multimedia data")
                    viewModel.restoreMultimedia()
                }
            }
        }

    /** Launches an activity to crop the image, using the [ImageCropper] */
    private val imageCropperLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            hasStartedImageSelection = false
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    result.data?.let {
                        val cropResultData =
                            IntentCompat.getParcelableExtra(
                                it,
                                CROP_IMAGE_RESULT,
                                ImageCropper.CropResultData::class.java,
                            )
                        Timber.d("Cropped image data: $cropResultData")

                        if (cropResultData?.uriPath == null || cropResultData.uriContent == null) return@registerForActivityResult
                        updateAndDisplayImageSize(cropResultData.uriPath)
                        viewModel.updateCurrentMultimediaPath(cropResultData.uriPath)
                        viewModel.updateCurrentMultimediaUri(cropResultData.uriContent)
                        previewImage(cropResultData.uriContent)
                    }
                }
                else -> {
                    Timber.v("Unable to crop the image")
                }
            }
        }

    /**
     * Lazily initialized instance of MultimediaMenu.
     * The instance is created only when first accessed.
     */
    private val multimediaMenu by lazy {
        MultimediaMenuProvider(
            menuResId = R.menu.multimedia_menu,
            onCreateMenuCondition = { menu ->

                setMenuItemIcon(menu.findItem(R.id.action_restart), R.drawable.ic_replace_image)
                lifecycleScope.launch {
                    viewModel.currentMultimediaUri.collectLatest { uri ->
                        menu.findItem(R.id.action_crop).isVisible = uri != null
                    }
                }
            },
        ) { menuItem ->
            when (menuItem.itemId) {
                R.id.action_crop -> {
                    viewModel.saveMultimediaForRevert(
                        imagePath = viewModel.currentMultimediaPath.value,
                        imageUri = viewModel.currentMultimediaUri.value,
                    )
                    requestCrop()
                    true
                }

                R.id.action_restart -> {
                    when (selectedImageOptions) {
                        ImageOptions.GALLERY -> {
                            openGallery()
                        }

                        ImageOptions.CAMERA -> {
                            viewModel.saveMultimediaForRevert(
                                imagePath = viewModel.currentMultimediaPath.value,
                                imageUri = viewModel.currentMultimediaUri.value,
                            )
                            dispatchCamera()
                        }

                        ImageOptions.DRAWING -> {
                            openDrawingCanvas()
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasStartedImageSelection = savedInstanceState?.getBoolean("HAS_STARTED_IMAGE_SELECTION", false) ?: false

        ankiCacheDirectory = FileUtil.getAnkiCacheDirectory(requireContext(), "temp-photos")
        if (ankiCacheDirectory == null) {
            Timber.e("createUI() failed to get cache directory")
            showErrorDialog(errorMessage = resources.getString(R.string.multimedia_editor_failed))
            return
        }

        arguments?.let {
            selectedImageOptions = it.getSerializableCompat<ImageOptions>(EXTRA_MEDIA_OPTIONS) as ImageOptions
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu(multimediaMenu)

        handleImageUri()
        setupDoneButton()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("HAS_STARTED_IMAGE_SELECTION", hasStartedImageSelection)
    }

    private fun handleImageUri() {
        fun processExternalImage(uri: Uri): Uri? = internalizeUri(uri)?.let { Uri.fromFile(it) }

        if (imageUri != null) {
            val internalUri = imageUri?.let { processExternalImage(it) }
            handleSelectImageIntent(internalUri)
        } else {
            handleSelectedImageOptions()
        }
    }

    private fun handleSelectedImageOptions() {
        if (hasStartedImageSelection) {
            Timber.d("Image selection already in progress, skipping.")
            return
        }
        hasStartedImageSelection = true

        when (selectedImageOptions) {
            ImageOptions.GALLERY -> {
                Timber.d("MultimediaImageFragment:: Opening gallery")
                openGallery()
            }
            ImageOptions.CAMERA -> {
                dispatchCamera()
                Timber.d("MultimediaImageFragment:: Launching camera")
            }

            ImageOptions.DRAWING -> {
                Timber.d("MultimediaImageFragment:: Opening drawing canvas")
                openDrawingCanvas()
            }
        }
    }

    private fun setupDoneButton() {
        binding.actionDone.setOnClickListener {
            Timber.d("MultimediaImageFragment:: Done button pressed")
            if (viewModel.selectedMediaFileSize == 0L) {
                Timber.d("Image length is not valid")
                return@setOnClickListener
            }
            if (viewModel.selectedMediaFileSize > IMAGE_LIMIT) {
                showLargeFileCropDialog(viewModel.selectedMediaFileSize)
                return@setOnClickListener
            }
            finishAddingImage()
        }
    }

    private fun finishAddingImage() {
        finishWithMedia()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        try {
            pickImageLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "MultimediaImageFragment:: No app found to select image")
            showSnackbar(R.string.activity_start_failed)
        }
    }

    private fun openDrawingCanvas() {
        val intent = DrawingFragment.getIntent(requireContext())
        drawingActivityLauncher.launch(intent)
    }

    private fun dispatchCamera() {
        val photoFile: File? =
            try {
                requireContext().createImageFile()
            } catch (e: Exception) {
                Timber.w(e, "Error creating the file")
                return
            }

        photoFile?.let {
            viewModel.updateCurrentMultimediaPath(it.absolutePath)
            val photoURI: Uri =
                FileProvider.getUriForFile(
                    requireContext(),
                    requireActivity().applicationContext.packageName + ".apkgfileprovider",
                    it,
                )

            try {
                cameraLauncher.launch(photoURI)
            } catch (e: ActivityNotFoundException) {
                Timber.w(e, "MultimediaImageFragment:: No camera found")
                showSnackbar(R.string.activity_start_failed)
            }
        }
    }

    private fun handleDrawingResult(intent: Intent) {
        val imageUri =
            BundleCompat.getParcelable(
                intent.extras!!,
                DrawingFragment.IMAGE_PATH_KEY,
                Uri::class.java,
            )

        if (imageUri == null) {
            Timber.w("handleDrawingResult() no image Uri provided")
            showSomethingWentWrong()
            return
        }

        val internalizedPick = internalizeUri(imageUri)

        if (internalizedPick == null) {
            Timber.w(
                "handleSelectImageIntent() unable to internalize image from Uri %s",
                imageUri,
            )
            showSomethingWentWrong()
            return
        }

        val drawImagePath = internalizedPick.absolutePath
        Timber.i("handleDrawingResult() Decoded image: '%s'", drawImagePath)

        previewImage(imageUri)
        viewModel.updateCurrentMultimediaPath(drawImagePath)
        viewModel.updateCurrentMultimediaUri(imageUri)
        updateAndDisplayImageSize(drawImagePath)
    }

    private fun handleTakePictureResult(imageFile: File?) {
        Timber.d("handleTakePictureResult, imageFile: %s", imageFile)
        if (imageFile == null) {
            Timber.i("handleTakePictureResult appears to have an invalid picture")
            return
        }

        viewModel.updateCurrentMultimediaPath(imageFile)
        viewModel.updateCurrentMultimediaUri(getUriForFile(imageFile))
        viewModel.currentMultimediaUri.value?.let { previewImage(it) }
        updateAndDisplayImageSize(imageFile)

        showCropDialog(getString(R.string.crop_image))
    }

    private fun updateAndDisplayImageSize(imageUri: String) {
        updateAndDisplayImageSize(File(imageUri))
    }

    private fun updateAndDisplayImageSize(file: File) {
        viewModel.selectedMediaFileSize = file.length()
        binding.imageFileSize.text = file.toHumanReadableSize()
    }

    private fun showLargeFileCropDialog(length: Long) {
        val numberFormat = NumberFormat.getInstance()
        // length is in bits, other elements have MB, convert to MB
        val size = numberFormat.format(length / 1000000.0)
        val message = getString(R.string.save_dialog_content, size)
        showCompressImageDialog(message)
    }

    private fun showCompressImageDialog(message: String) {
        AlertDialog.Builder(requireActivity()).show {
            message(text = message)
            positiveButton(R.string.compress) {
                viewModel.currentMultimediaPath.value.let {
                    if (it == null) return@positiveButton
                    if (!rotateAndCompress(it)) {
                        Timber.d("Unable to compress the clicked image")
                        showErrorDialog(errorMessage = resources.getString(R.string.multimedia_editor_image_compression_failed))
                        return@positiveButton
                    }
                }
            }
            negativeButton(R.string.dialog_no) {
                finishAddingImage()
            }
        }
    }

    private fun showCropDialog(message: String) {
        if (viewModel.currentMultimediaUri.value == null) {
            Timber.w("showCropDialog called with null URI or Path")
            return
        }

        AlertDialog.Builder(requireActivity()).show {
            message(text = message)
            positiveButton(R.string.dialog_yes) {
                requestCrop()
            }
            negativeButton(R.string.dialog_no)
        }
    }

    /**
     * Handles the selected image from the intent by previewing it in a WebView and internalizing the URI.
     * Updates the ViewModel with the selected image's path and size.
     *
     * @param imageUri The URI of the selected image.
     */
    private fun handleSelectImageIntent(imageUri: Uri?) {
        if (imageUri == null) {
            Timber.w("handleSelectImageIntent() selectedImage was null")
            showSomethingWentWrong()
            return
        }

        previewImage(imageUri)

        val internalFile = resolveUriToFile(imageUri)
        if (internalFile == null || !internalFile.exists()) {
            showSomethingWentWrong()
            return
        }

        // Update ViewModel with image data
        val imagePath = internalFile.absolutePath

        try {
            // if that worked, the image was not too large / good format, update viewModel
            viewModel.updateCurrentMultimediaUri(imageUri)
            viewModel.updateCurrentMultimediaPath(imagePath)
        } catch (e: Exception) {
            Timber.w(e, "handleSelectImageIntent() unable to set image for preview")
            // clear the image out of the preview so we may recover
            showSomethingWentWrong()
            return
        }
        viewModel.selectedMediaFileSize = internalFile.length()

        // Optionally update and display the image size
        updateAndDisplayImageSize(imagePath)
    }

    /**
     * Resolves a [Uri] to a [File] on internal storage.
     *
     * If the URI is a content URI, it is internalized by copying its contents to internal storage.
     * If the URI is already a file URI, it is directly converted to a [File] object.
     *
     * @param uri The URI to resolve.
     * @return The corresponding [File], or `null` if the URI is invalid or unsupported.
     */
    private fun resolveUriToFile(uri: Uri): File? {
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> File(uri.path ?: return null)
            else -> internalizeUri(uri)
        }
    }

    /**
     * Previews the selected image in a WebView.
     * Handles both SVG and non-SVG images (e.g., JPG, PNG) and displays the image based on its MIME type.
     *
     * @param imageUri The URI of the selected image.
     */
    private fun previewImage(imageUri: Uri) {
        val mimeType = context?.contentResolver?.getType(imageUri)

        // Get the WebView and set it visible
        binding.multimediaWebView.apply {
            visibility = View.VISIBLE

            // Load image based on its MIME type
            // SVGs require special handling due to their XML-based format and rendering complexities.
            // Raster images (e.g., JPG, PNG) can be rendered directly using an <img> tag in HTML.
            when (mimeType) {
                SVG_IMAGE -> loadSvgImage(imageUri)
                else -> loadImage(imageUri)
            }
        }
    }

    /**
     * Loads and previews an SVG image in the WebView.
     *
     * @param imageUri The URI of the SVG image.
     */
    private fun WebView.loadSvgImage(imageUri: Uri) {
        val svgData = loadSvgFromUri(imageUri)
        if (svgData != null) {
            Timber.i("Selected image is an SVG.")

            loadDataWithBaseURL(null, svgData, SVG_IMAGE, "UTF-8", null)
        } else {
            Timber.w("Failed to load SVG from URI")
            showErrorInWebView()
        }
    }

    /**
     * Loads and previews a non-SVG image (e.g., JPG, PNG) in the WebView.
     *
     * @param imageUri The URI of the non-SVG image.
     */
    private fun WebView.loadImage(imageUri: Uri) {
        Timber.i("Loading non-SVG image using WebView")

        try {
            val internalFile = internalizeUri(imageUri)?.takeIf { it.exists() }
            if (internalFile == null) {
                Timber.w(
                    "loadImage() unable to internalize image from Uri %s",
                    imageUri,
                )
                showSomethingWentWrong()
                return
            }

            val contentUri = getContentUriFromFile(internalFile)
            if (contentUri == null) {
                Timber.w("Failed to get content URI for the image.")
                showSomethingWentWrong()
                return
            }

            val htmlData =
                """
                <html>
                    <body style="margin:0;padding:0;">
                        <img src="$contentUri" style="width:100%;height:auto;" />
                    </body>
                </html>
                """.trimIndent()

            loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            Timber.e(e, "Error loading image in WebView")
            showSomethingWentWrong()
        }
    }

    private fun getContentUriFromFile(file: File): Uri? {
        val context = context ?: return null
        val authority = context.applicationContext.packageName + ".apkgfileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /** Shows an error image along with an error text **/
    private fun showErrorInWebView() {
        val base64Image = getBitmapDrawable(R.drawable.ic_image_not_supported).toBase64Png()

        val errorHtml =
            """
            <html>
                <body style="text-align:center;">
                    <img src="data:image/png;base64,$base64Image" alt="${TR.notetypeErrorNoImageToShow()}"/>
                </body>
            </html>
            """.trimIndent()

        binding.multimediaWebView.loadDataWithBaseURL(null, errorHtml, "text/html", "UTF-8", null)
    }

    private fun requestCrop() {
        val imageUri = viewModel.currentMultimediaUri.value ?: return
        Timber.i("launching crop")
        hasStartedImageSelection = true
        val intent =
            com.ichi2.imagecropper.ImageCropperLauncher
                .ImageUri(imageUri)
                .getIntent(requireContext())
        imageCropperLauncher.launch(intent)
    }

    /**
     * Loads an SVG file from the given URI and returns its content as a string.
     *
     * @param uri The URI of the SVG file to be loaded.
     * @return The content of the SVG file as a string, or null if an error occurs.
     */
    private fun loadSvgFromUri(uri: Uri): String? =
        try {
            context?.contentResolver?.openInputStreamSafe(uri)?.use { inputStream ->
                inputStream.convertToString()
            }
        } catch (e: Exception) {
            Timber.w(e, "Error reading SVG from URI")
            null
        }

    /**
     * Rotate and compress the image, with the side effect of the current image being backed by a new file
     *
     * @return true if successful, false indicates the current image is likely not usable, revert if possible
     */
    private fun rotateAndCompress(imageFile: File): Boolean {
        Timber.d("rotateAndCompress() on %s", imageFile)

        // Set the rotation of the camera image and save as JPEG
        Timber.d("rotateAndCompress in path %s has size %d", imageFile.absolutePath, imageFile.length())

        // Load into a bitmap with max size of 1920 pixels and rotate if necessary
        var bitmap = BitmapUtil.decodeFile(imageFile, IMAGE_SAVE_MAX_WIDTH)
        if (bitmap == null) {
            Timber.w("rotateAndCompress() unable to decode file %s", imageFile)
            return false
        }

        var out: FileOutputStream? = null
        try {
            val outFile = createNewCacheImageFile(directory = ankiCacheDirectory)
            out = FileOutputStream(outFile)

            // Rotate the bitmap if needed
            bitmap = ExifUtil.rotateFromCamera(imageFile, bitmap)

            // Compress the bitmap to JPEG format with 90% quality
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)

            // Delete the original image file
            if (!imageFile.delete()) {
                Timber.w("rotateAndCompress() delete of pre-compressed image failed %s", imageFile)
            }

            val imageUri = getUriForFile(outFile)

            // TODO: see if we can use one value to the viewModel
            viewModel.updateCurrentMultimediaUri(imageUri)
            viewModel.updateCurrentMultimediaPath(outFile.path)
            previewImage(imageUri)
            viewModel.selectedMediaFileSize = outFile.length()
            updateAndDisplayImageSize(outFile.path)

            Timber.d("rotateAndCompress out path %s has size %d", outFile.absolutePath, outFile.length())
        } catch (e: FileNotFoundException) {
            Timber.w(e, "rotateAndCompress() File not found for image compression %s", imageFile)
            return false
        } catch (e: IOException) {
            Timber.w(e, "rotateAndCompress() create file failed for file %s", imageFile)
            return false
        } finally {
            try {
                out?.close()
            } catch (e: IOException) {
                Timber.w(e, "rotateAndCompress() Unable to clean up image compression output stream")
            }
        }

        return true
    }

    private fun internalizeUri(uri: Uri): File? {
        val internalFile: File
        val uriFileName = MultimediaUtils.getImageNameFromUri(requireContext(), uri)

        // Use the display name from the image info to create a new file with correct extension
        if (uriFileName == null) {
            Timber.w("internalizeUri() unable to get file name")
            showSomethingWentWrong()
            return null
        }
        internalFile =
            try {
                createCachedFile(uriFileName, ankiCacheDirectory)
            } catch (e: IOException) {
                Timber.w(e, "internalizeUri() failed to create new file with name %s", uriFileName)
                showSomethingWentWrong()
                return null
            }
        return try {
            val returnFile =
                FileUtil.internalizeUri(uri, internalFile, requireActivity().contentResolver)
            Timber.d("internalizeUri successful. Returning internalFile.")
            returnFile
        } catch (e: Exception) {
            Timber.w(e)
            showSomethingWentWrong()
            null
        }
    }

    private fun getImageUri(data: Intent): Uri? {
        Timber.d("getImageUri for data %s", data)
        val uri = data.data
        if (uri == null) {
            showSnackbar(getString(R.string.select_image_failed))
        }
        return uri
    }

    companion object {
        fun getIntent(
            context: Context,
            multimediaExtra: MultimediaActivityExtra,
            imageOptions: ImageOptions,
        ): Intent =
            MultimediaActivity.getIntent(
                context,
                MultimediaImageFragment::class,
                multimediaExtra,
                imageOptions,
            )
    }

    /** Image options that a user choose from the bottom sheet which [MultimediaImageFragment] uses **/
    enum class ImageOptions {
        GALLERY,
        CAMERA,
        DRAWING,
    }
}

private fun Fragment.getBitmapDrawable(
    @DrawableRes resId: Int,
) = ContextCompat.getDrawable(requireContext(), resId) as BitmapDrawable
