// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multimedia

import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.dialogs.DiscardChangesDialog
import com.ichi2.anki.multimediacard.IMultimediaEditableNote
import com.ichi2.anki.multimediacard.fields.IField
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.bottomCornerClearance
import com.ichi2.anki.utils.ext.setSupportActionBarText
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

    private val argsStorage by lazy { MultimediaArgsStorage.create(requireContext()) }

    protected var ankiCacheDirectory: String? = null

    protected var indexValue: Int = 0
    protected lateinit var field: IField
    protected lateinit var note: IMultimediaEditableNote
    protected var imageUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val multimediaActivityExtra =
            try {
                arguments
                    ?.getSerializableCompat<File>(MultimediaActivity.EXTRA_FRAGMENT_ARGS)
                    ?.let(argsStorage::read)
            } catch (e: Exception) {
                Timber.w(e, "Unable to load multimedia arguments")
                showErrorDialog()
                return null
            }
        if (multimediaActivityExtra != null) {
            indexValue = multimediaActivityExtra.index
            field = multimediaActivityExtra.field
            note = multimediaActivityExtra.note
            if (multimediaActivityExtra.imageUri != null) {
                imageUri = multimediaActivityExtra.imageUri.toUri()
            }
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Keep the input while the fragment can be restored after recreation or process death.
        if (isRemoving || activity?.isFinishing == true) {
            arguments?.getSerializableCompat<File>(MultimediaActivity.EXTRA_FRAGMENT_ARGS)?.delete()
        }
    }

    @NeedsTest("test discard dialog shown in case there are changes")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        setupEdgeToEdge(view)

        setSupportActionBarText(title = title)

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
     * Applies edge-to-edge insets for the screen. [MultimediaActivity] is the only host, and it
     * insets the app bar above this fragment, so no top inset is taken here.
     */
    private fun setupEdgeToEdge(view: View) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { root, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
            root.updatePadding(
                left = bars.left,
                right = bars.right,
                // 'action_done' is the bottom-most touch target, so it clears the corners too
                bottom = maxOf(bars.bottom, insets.bottomCornerClearance(root)),
            )
            insets
        }
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
        showSnackbar(resources.getString(CommonString.multimedia_editor_something_wrong))
    }

    /**
     * Creates and shows an AlertDialog with an error message
     * from the application's resources. Acknowledging or cancelling the dialog finishes
     * the current activity.
     */
    fun showErrorDialog(errorMessage: String? = null) {
        AlertDialog.Builder(requireContext()).show {
            setMessage(errorMessage ?: resources.getString(CommonString.something_wrong))
            setPositiveButton(getString(CommonString.dialog_ok)) { _, _ ->
                requireActivity().finish()
            }
            setOnCancelListener { requireActivity().finish() }
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
