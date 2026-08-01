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

import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.fields.IField
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.show
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

/**
 * Abstract base class for fragments that handle multimedia operations.
 *
 * This class provides a common framework for fragments that need to handle multimedia operations,
 * including caching directories, managing fields and notes, and setting toolbar titles.
 *
 * @param layout The layout resource ID to be inflated by this fragment.
 *
 * @see MultimediaActivity
 */
abstract class MultimediaFragment(
    @LayoutRes layout: Int,
) : Fragment(layout) {
    abstract val title: String

    val viewModel: MultimediaViewModel by viewModels()

    protected var ankiCacheDirectory: String? = null

    protected var indexValue: Int = 0
    protected lateinit var field: IField
    protected lateinit var note: IMultimediaEditableNote
    protected var imageUri: Uri? = null

    @NeedsTest("test discard dialog shown in case there are changes")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        requireAnkiActivity().setToolbarText(title = title)

        if (arguments != null) {
            Timber.d("Getting MultimediaActivityExtra values from arguments")
            @Suppress("USELESS_CAST")
            val multimediaActivityExtra =
                arguments?.getSerializableCompat<MultimediaActivityExtra>(
                    MultimediaActivity.EXTRA_FRAGMENT_ARGS,
                )

            if (multimediaActivityExtra != null) {
                indexValue = multimediaActivityExtra.index
                field = multimediaActivityExtra.field
                note = multimediaActivityExtra.note
                if (multimediaActivityExtra.imageUri != null) {
                    imageUri = multimediaActivityExtra.imageUri.toUri()
                }
            }
        }

        val backCallback =
            object : OnBackPressedCallback(
                enabled = viewModel.currentMultimediaPath.value != null,
            ) {
                override fun handleOnBackPressed() {
                    DiscardChangesDialog.showDialog(requireContext(), message = TR.addingDiscardCurrentInput()) {
                        Timber.i("MultimediaFragment:: OK button pressed to confirm discard changes")
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }

        lifecycleScope.launch {
            viewModel.currentMultimediaPath.collectLatest { value ->
                backCallback.isEnabled = value != null
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
    }

    /**
     * Get Uri based on current image path
     *
     * @param file the file to get URI for
     * @return current image path's uri
     */
    fun getUriForFile(file: File): Uri {
        Timber.d("getUriForFile() %s", file)
        try {
            return FileProvider.getUriForFile(
                requireActivity(),
                requireActivity().applicationContext.packageName + ".apkgfileprovider",
                file,
            )
        } catch (e: Exception) {
            // #6628 - What would cause this? Is the fallback is effective? Telemetry to diagnose more:
            Timber.w(e, "getUriForFile failed on %s - attempting fallback", file)
            CrashReportService.sendExceptionReport(e, "MultimediaFragment", "Unexpected getUriForFile failure on $file", true)
        }
        return Uri.fromFile(file)
    }

    fun setMenuItemIcon(
        menuItem: MenuItem,
        @DrawableRes icon: Int,
    ) {
        menuItem.icon = ContextCompat.getDrawable(requireContext(), icon)
    }

    /**
     * Attaches a `MenuProvider` to the activity for creating its menu.
     *
     * @param menuProvider An instance of the `MenuProvider` class that will be responsible for inflating and configuring the menu.
     */
    // TODO: move this to requireAnkiActivity().addMenuProvider()
    fun setupMenu(menuProvider: MenuProvider) {
        (requireActivity() as MenuHost).addMenuProvider(menuProvider)
    }

    fun File.toHumanReadableSize(): String = Formatter.formatFileSize(requireContext(), this.length())

    /**
     * Shows a Snackbar at the bottom of the screen with a predefined
     * "Something went wrong" message from the application's resources.
     */
    fun showSomethingWentWrong() {
        showSnackbar(resources.getString(R.string.multimedia_editor_something_wrong))
    }

    /**
     * Creates and shows an AlertDialog with an error message
     * from the application's resources. The dialog includes an "OK" button that,
     * when clicked, finishes the current activity.
     */
    fun showErrorDialog(errorMessage: String? = null) {
        AlertDialog.Builder(requireContext()).show {
            setMessage(errorMessage ?: resources.getString(R.string.something_wrong))
            setPositiveButton(getString(R.string.dialog_ok)) { _, _ ->
                requireActivity().finish()
            }
        }
    }

    /**
     * Finishes the activity with a [MultimediaResult.Cancelled] result when no media
     * has been captured yet. Call from child-launcher cancel branches to propagate
     * the cancellation without losing partial captures.
     */
    protected fun cancelIfEmpty() {
        if (viewModel.currentMultimediaUri.value == null) {
            setMultimediaResultAndFinish(MultimediaResult.Cancelled(indexValue))
        }
    }

    /**
     * Attaches the currently captured media to [field] and finishes the activity
     * with a [MultimediaResult.Success]. Call from confirm/done actions.
     */
    protected fun finishWithMedia() {
        field.mediaFile = viewModel.currentMultimediaPath.value
        field.hasTemporaryMedia = true
        setMultimediaResultAndFinish(MultimediaResult.Success(indexValue, field))
    }
}
