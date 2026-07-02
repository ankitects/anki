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
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.databinding.FragmentAudioVideoBinding
import com.ichi2.anki.multimedia.AudioVideoFragment.MediaOption.AUDIO_CLIP
import com.ichi2.anki.multimedia.AudioVideoFragment.MediaOption.VIDEO_CLIP
import com.ichi2.anki.multimedia.MultimediaActivity.Companion.EXTRA_MEDIA_OPTIONS
import com.ichi2.anki.multimedia.MultimediaUtils.createCachedFile
import com.ichi2.anki.utils.ext.sharedPrefs
import com.ichi2.utils.ExceptionUtil.executeSafe
import com.ichi2.utils.FileUtil
import com.ichi2.utils.openInputStreamSafe
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber
import java.io.File

/** Handles the Multimedia Audio and Video attachment in the NoteEditor */
class AudioVideoFragment : MultimediaFragment(R.layout.fragment_audio_video) {
    private val binding by viewBinding(FragmentAudioVideoBinding::bind)
    private lateinit var selectedMediaOptions: MediaOption

    override val title: String
        get() = getTitleForFragment(selectedMediaOptions, requireContext())

    /**
     * Launches an activity to pick audio or video file from the device
     */
    private val pickMediaLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when {
                result.resultCode != Activity.RESULT_OK || result.data == null -> {
                    Timber.d("Uri is empty or Result not OK")
                    cancelIfEmpty()
                }
                else -> {
                    executeSafe(requireContext(), "pickMediaLauncher:unhandled") {
                        handleMediaSelection(result.data!!)
                    }
                }
            }
        }

    /**
     * Lazily initialized instance of MultimediaMenu.
     * The instance is created only when first accessed.
     */
    @NeedsTest("The menu drawable icon should be correctly set")
    private val multimediaMenu by lazy {
        MultimediaMenuProvider(
            menuResId = R.menu.multimedia_menu,
            onCreateMenuCondition = { menu ->
                setMenuItemIcon(
                    menu.findItem(R.id.action_restart),
                    if (selectedMediaOptions == AUDIO_CLIP) R.drawable.ic_replace_audio else R.drawable.ic_replace_video,
                )
                menu.findItem(R.id.action_crop).isVisible = false
            },
        ) { menuItem ->
            when (menuItem.itemId) {
                R.id.action_restart -> {
                    handleSelectedMediaOptions()
                    true
                }

                else -> false
            }
        }
    }

    private lateinit var mediaPlayer: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ankiCacheDirectory = FileUtil.getAnkiCacheDirectory(requireContext(), "temp-media")
        if (ankiCacheDirectory == null) {
            showErrorDialog()
            Timber.e("createUI() failed to get cache directory")
            return
        }

        arguments?.let {
            selectedMediaOptions = it.getSerializableCompat<MediaOption>(EXTRA_MEDIA_OPTIONS) as MediaOption
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu(multimediaMenu)

        setupMediaPlayer()

        handleSelectedMediaOptions()

        setupDoneButton()
    }

    private fun handleSelectedMediaOptions() {
        when (selectedMediaOptions) {
            AUDIO_CLIP -> {
                Timber.d("Opening chooser for audio file")
                openMediaChooser(
                    "audio/*",
                    // #9226: allows ogg on Android 8
                    arrayOf("audio/*", "application/ogg"),
                    R.string.multimedia_editor_popup_audio_clip,
                )
            }

            VIDEO_CLIP -> {
                Timber.d("Opening chooser for video file")
                openMediaChooser(
                    "video/*",
                    emptyArray(),
                    R.string.multimedia_editor_popup_video_clip,
                )
            }
        }
    }

    // defaultArtwork(Unstable API) is required for audio files cover otherwise it shows an empty black screen
    @OptIn(UnstableApi::class)
    private fun setupMediaPlayer() {
        Timber.d("Setting up media player")
        mediaPlayer =
            ExoPlayer
                .Builder(requireContext())
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setContentType(
                            C.AUDIO_CONTENT_TYPE_MUSIC,
                        ).build(),
                    true,
                ).build()
        binding.playerView.player = mediaPlayer
        binding.playerView.setControllerAnimationEnabled(true)

        if (selectedMediaOptions == AUDIO_CLIP) {
            Timber.d("Media file is of audio type, setting default artwork")
            binding.playerView.defaultArtwork =
                ContextCompat.getDrawable(requireContext(), R.drawable.round_audio_file_24)
        }
    }

    private fun setupDoneButton() {
        binding.actionDone.setOnClickListener {
            Timber.d("MultimediaImageFragment:: Done button pressed")
            if (viewModel.selectedMediaFileSize == 0L) {
                Timber.d("Audio or Video length is not valid")
                return@setOnClickListener
            }
            finishWithMedia()
        }
    }

    /**
     * Opens a media chooser to allow the user to select a media file.
     *
     * This method first checks the shared preference identified by the key "mediaImportAllowAllFiles" to determine if the user allows importing all file types.
     * Based on this setting, it configures an `Intent` object to specify the desired media type(s).
     * If "allowAllFiles" is true, the intent will accept any file type
     *
     * @param initialMimeType The initial mime type to be used for the media selection.
     * @param extraMimeTypes An optional array of additional mime types to accept besides the initial one.
     * @param prompt The resource ID of a string to be used as the prompt for the media chooser dialog. This parameter should be annotated with `@StringRes`.
     */
    private fun openMediaChooser(
        initialMimeType: String,
        extraMimeTypes: Array<String>,
        @StringRes prompt: Int,
    ) {
        val allowAllFiles =
            sharedPrefs().getBoolean("mediaImportAllowAllFiles", false)
        val intent = Intent()
        intent.type = if (allowAllFiles) "*/*" else initialMimeType
        if (!allowAllFiles && extraMimeTypes.any()) {
            // application/ogg takes precedence over "*/*" for application/octet-stream
            // so don't add it if we're want */*
            intent.putExtra(Intent.EXTRA_MIME_TYPES, extraMimeTypes)
        }
        intent.action = Intent.ACTION_GET_CONTENT
        // Only get openable files, to avoid virtual files issues with Android 7+
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        val chooserPrompt = resources.getString(prompt)
        pickMediaLauncher.launch(Intent.createChooser(intent, chooserPrompt))
    }

    private fun handleMediaSelection(data: Intent) {
        Timber.d("Handling media selection")
        val selectedMediaClip = data.data

        if (selectedMediaClip == null) {
            Timber.w("Media file is null")
            showErrorDialog()
            return
        }

        viewModel.updateCurrentMultimediaUri(selectedMediaClip)

        prepareMediaPlayer(selectedMediaClip)
        val mediaClipFullNameParts = getMediaFileDetails(selectedMediaClip) ?: return
        val clipCopy = createTempMediaFile(mediaClipFullNameParts) ?: return
        copyMediaFileToTemp(selectedMediaClip, clipCopy)
    }

    private fun prepareMediaPlayer(uri: Uri) {
        Timber.d("Preparing media player")
        val mediaItem = MediaItem.fromUri(uri)
        mediaPlayer.setMediaItem(mediaItem)
        mediaPlayer.prepare()
    }

    /**
     * Retrieves details about a selected media clip from the MediaStore.
     *
     * This method takes a `Uri` representing the selected media clip as input.
     *
     * If the query is successful, it processes the retrieved data as follows:
     *   * It parses the display name to extract the filename and extension.
     *     * If the display name contains a single dot (.), it assumes the second half is the extension.
     *     * If the display name contains multiple dots (.), it extracts the part before the last dot as the filename and the part after the last dot as the extension.
     *     * If there is no dot (.) in the name, it attempts to use the second part of the MIME type as the extension.
     *   * In case of any errors during parsing, it sends an exception report. It also displays an error message using `showSomethingWentWrong()`.
     *
     * The method returns an array of strings containing the filename and extension (if available), or null if any errors occur during processing.
     *
     * @param selectedMediaClip The Uri representing the selected media clip.
     * @return An array of strings containing the filename and extension of the media clip, or null on error.
     */
    private fun getMediaFileDetails(selectedMediaClip: Uri): Array<String>? {
        val queryColumns =
            arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
            )
        var mediaClipFullNameParts: Array<String>
        requireContext()
            .contentResolver
            .query(selectedMediaClip, queryColumns, null, null, null)
            .use { cursor ->
                if (cursor == null) {
                    showSomethingWentWrong()
                    return null
                }
                cursor.moveToFirst()
                val mediaClipFullName = cursor.getString(0)
                mediaClipFullNameParts = mediaClipFullName.split(".").toTypedArray()
                if (mediaClipFullNameParts.size < 2) {
                    mediaClipFullNameParts =
                        try {
                            Timber.i("Media clip name does not have extension, using second half of mime type")
                            arrayOf(mediaClipFullName, cursor.getString(2).split("/").toTypedArray()[1])
                        } catch (e: Exception) {
                            Timber.w(e)
                            CrashReportService.sendExceptionReport(
                                e,
                                "Media Clip addition failed. Name $mediaClipFullName / cursor mime type column type " +
                                    cursor.getType(
                                        2,
                                    ),
                            )
                            showSomethingWentWrong()
                            return null
                        }
                } else if (mediaClipFullNameParts.size > 2) {
                    val lastPointIndex = mediaClipFullName.lastIndexOf(".")
                    mediaClipFullNameParts =
                        arrayOf(
                            mediaClipFullName.substring(0 until lastPointIndex),
                            mediaClipFullName.substring(lastPointIndex + 1),
                        )
                }
            }
        return mediaClipFullNameParts
    }

    /**
     * Creates a temporary media file based on the provided filename and extension.
     *
     * @param mediaClipFullNameParts An array of strings containing the filename and extension of the media clip.
     * @return A File object representing the created temporary media file, or null on error.
     */
    private fun createTempMediaFile(mediaClipFullNameParts: Array<String>): File? =
        try {
            val clipCopy =
                createCachedFile(
                    "${mediaClipFullNameParts[0]}.${mediaClipFullNameParts[1]}",
                    ankiCacheDirectory,
                )
            Timber.d("media clip picker file path is: %s", clipCopy.absolutePath)
            clipCopy
        } catch (e: Exception) {
            Timber.e(e, "Could not create temporary media file. ")
            CrashReportService.sendExceptionReport(e, "handleMediaSelection:tempFile")
            showSomethingWentWrong()
            null
        }

    /**
     * Copies a selected media clip to a temporary file.
     *
     * @param selectedMediaClip The Uri representing the selected media clip.
     * @param clipCopy The File object representing the temporary file where the media clip will be copied.
     */
    private fun copyMediaFileToTemp(
        selectedMediaClip: Uri,
        clipCopy: File,
    ) {
        try {
            requireContext().contentResolver.openInputStreamSafe(selectedMediaClip).use { inputStream ->
                CompatHelper.compat.copyFile(inputStream!!, clipCopy.absolutePath)

                viewModel.updateCurrentMultimediaPath(clipCopy)
                viewModel.selectedMediaFileSize = clipCopy.length()
                binding.mediaFileSize.text = clipCopy.toHumanReadableSize()
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to copy media file from ContentProvider")
            CrashReportService.sendExceptionReport(e, "handleMediaSelection:copyFromProvider")
            showSomethingWentWrong()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Timber.d("Releasing media player")
        mediaPlayer.release()
    }

    override fun onStop() {
        super.onStop()
        Timber.d("Stopping media player")
        mediaPlayer.playWhenReady = false
    }

    companion object {
        fun getIntent(
            context: Context,
            multimediaExtra: MultimediaActivityExtra,
            mediaOptions: MediaOption,
        ): Intent =
            MultimediaActivity.getIntent(
                context,
                AudioVideoFragment::class,
                multimediaExtra,
                mediaOptions,
            )
    }

    /** The supported media types that a user choose from the bottom sheet which [AudioVideoFragment] uses */
    enum class MediaOption {
        AUDIO_CLIP,
        VIDEO_CLIP,
    }

    /**
     * Returns the appropriate title string based on the current media option.
     *
     * @param mediaOption MediaOption The media option for which the title string is to be returned.
     * @param context Context The context to use for accessing string resources.
     * @return String The title string corresponding to the current media option.
     */
    private fun getTitleForFragment(
        mediaOption: MediaOption,
        context: Context,
    ): String =
        when (mediaOption) {
            AUDIO_CLIP -> context.getString(R.string.multimedia_editor_popup_audio_clip)
            VIDEO_CLIP -> context.getString(R.string.multimedia_editor_popup_video_clip)
        }
}
