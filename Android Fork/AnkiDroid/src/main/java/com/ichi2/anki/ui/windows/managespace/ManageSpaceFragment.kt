// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brian Da Silva <brianjose2010@gmail.com>

package com.ichi2.anki.ui.windows.managespace

import android.app.ActivityManager
import android.app.Application
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.Preference
import com.ichi2.anki.BackupManager
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
import com.ichi2.anki.LocalizedUnambiguousBackupTimeFormatter
import com.ichi2.anki.R
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.anki.preferences.requirePreference
import com.ichi2.anki.progress.HasProgress
import com.ichi2.anki.progress.ProgressManager
import com.ichi2.anki.progress.observeProgress
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.dialogs.tools.AsyncDialogBuilder.CheckedItems
import com.ichi2.anki.ui.dialogs.tools.DialogResult
import com.ichi2.anki.ui.dialogs.tools.awaitDialog
import com.ichi2.anki.utils.ext.collectInViewLifecycle
import com.ichi2.anki.utils.getUserFriendlyErrorText
import com.ichi2.preferences.TextWidgetPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.system.exitProcess

sealed interface Size {
    data object Calculating : Size

    class Bytes(
        val totalSize: Long,
    ) : Size

    class FilesAndBytes(
        val files: Collection<File>,
        val totalSize: Long,
    ) : Size

    class Error(
        val exception: Exception,
        @StringRes val widgetTextId: Int = CommonString.pref__widget_text__error,
    ) : Size
}

/**************************************************************************************************
 ********************************************* Model **********************************************
 **************************************************************************************************/

class ManageSpaceViewModel(
    val app: Application,
) : AndroidViewModel(app),
    CollectionDirectoryProvider,
    HasProgress<ManageSpaceProgress> {
    override val collectionDirectory = CollectionManager.getCollectionDirectory()
    override val progressManager = ProgressManager<ManageSpaceProgress>()

    val flowOfDeleteUnusedMediaSize = MutableStateFlow<Size>(Size.Calculating)
    val flowOfDeleteBackupsSize = MutableStateFlow<Size>(Size.Calculating)
    val flowOfDeleteCollectionSize = MutableStateFlow<Size>(Size.Calculating)
    val flowOfDeleteEverythingSize = MutableStateFlow<Size>(Size.Calculating)

    init {
        launchCalculationOfBackupsSize()
        launchCalculationOfSizeOfEverything()
        launchCalculationOfCollectionSize()
        launchSearchForUnusedMedia()
    }

    /************************************* Unused media files *************************************/

    private fun launchSearchForUnusedMedia() =
        viewModelScope.launch {
            flowOfDeleteUnusedMediaSize.ifCollectionDirectoryExistsEmit {
                withCol {
                    val unusedFiles = media.findUnusedMediaFiles()
                    val unusedFilesSize = unusedFiles.sumOf(::calculateSize)
                    Size.FilesAndBytes(unusedFiles, unusedFilesSize)
                }
            }
        }

    suspend fun deleteMediaFiles(filesNamesToDelete: List<String>) {
        progressManager.withProgress(message = ManageSpaceProgress.DELETING_MEDIA) {
            try {
                withCol {
                    media.trashFiles(filesNamesToDelete)
                    media.emptyTrash()
                }
            } finally {
                launchCalculationOfSizeOfEverything()
                launchCalculationOfCollectionSize()
                launchSearchForUnusedMedia()
            }
        }
    }

    /***************************************** Backups ********************************************/

    private fun launchCalculationOfBackupsSize() =
        viewModelScope.launch {
            flowOfDeleteBackupsSize.ifCollectionDirectoryExistsEmit {
                withCol {
                    val backupFiles = BackupManager.getBackups(colDb).toList()
                    val backupFilesSize = backupFiles.sumOf(::calculateSize)
                    Size.FilesAndBytes(backupFiles, backupFilesSize)
                }
            }
        }

    suspend fun deleteBackups(backupsToDelete: List<File>) {
        progressManager.withProgress(message = ManageSpaceProgress.DELETING_BACKUPS) {
            try {
                withCol { BackupManager.deleteBackups(this@withCol, backupsToDelete) }
            } finally {
                launchCalculationOfBackupsSize()
                launchCalculationOfCollectionSize()
                launchCalculationOfSizeOfEverything()
            }
        }
    }

    /*************************************** Collection *******************************************/

    private fun launchCalculationOfCollectionSize() =
        viewModelScope.launch {
            flowOfDeleteCollectionSize.ifCollectionDirectoryExistsEmit {
                withContext(ioDispatcher) {
                    Size.Bytes(calculateSize(collectionDirectory))
                }
            }
        }

    suspend fun deleteCollection() {
        progressManager.withProgress(message = ManageSpaceProgress.DELETING_COLLECTION) {
            try {
                CollectionManager.deleteCollectionDirectory() // Executed in withQueue
            } finally {
                launchCalculationOfBackupsSize()
                launchCalculationOfSizeOfEverything()
                launchCalculationOfCollectionSize()
                launchSearchForUnusedMedia()
            }
        }
    }

    /*************************************** Everything *******************************************/

    private fun launchCalculationOfSizeOfEverything() =
        viewModelScope.launch {
            flowOfDeleteEverythingSize.emit(Size.Calculating)
            flowOfDeleteEverythingSize.emit(
                withContext(ioDispatcher) {
                    try {
                        Size.Bytes(app.getUserDataAndCacheSize())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Size.Error(e)
                    }
                },
            )
        }

    // This kills the process afterwards, so no need to recalculate sizes
    fun deleteEverything() {
        getSystemService(app, ActivityManager::class.java)?.clearApplicationUserData()
    }

    /**********************************************************************************************/

    private suspend fun MutableStateFlow<Size>.ifCollectionDirectoryExistsEmit(block: suspend () -> Size) {
        try {
            ensureCanWriteToOrCreateCollectionDirectory()
            if (!collectionDirectoryExists()) {
                emit(Size.FilesAndBytes(emptyList(), 0L))
            } else {
                emit(Size.Calculating)
                emit(block())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Size.Error(e))
        }
    }
}

enum class ManageSpaceProgress {
    DELETING_MEDIA,
    DELETING_BACKUPS,
    DELETING_COLLECTION,
}

/**************************************************************************************************
 ******************************************** Fragment ********************************************
 **************************************************************************************************/

class ManageSpaceFragment : SettingsFragment() {
    override val preferenceResource = R.xml.manage_space
    override val analyticsScreenNameConstant = "manageSpace"

    private val viewModel: ManageSpaceViewModel by viewModels()

    override fun initSubscreen() {
        val deleteUnusedMediaPreference = requirePreference<TextWidgetPreference>(R.string.pref_delete_media_key)
        val deleteBackupsPreference = requirePreference<TextWidgetPreference>(R.string.pref_delete_backups_key)
        val deleteCollectionPreference = requirePreference<TextWidgetPreference>(R.string.pref_delete_collection_key)
        val deleteEverythingPreference = requirePreference<TextWidgetPreference>(R.string.pref_delete_everything_key)

        deleteUnusedMediaPreference.launchOnPreferenceClick { onDeleteUnusedMediaClick() }
        deleteBackupsPreference.launchOnPreferenceClick { onDeleteBackupsClick() }
        deleteCollectionPreference.launchOnPreferenceClick { onDeleteCollectionClick() }
        deleteEverythingPreference.launchOnPreferenceClick { onDeleteEverythingClick() }

        adjustDeleteEverythingStringsDependingOnCollectionLocation(deleteEverythingPreference)

        observeProgress(viewModel) { progress -> getString(progress.messageRes) }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.apply {
            title = getString(CommonString.pref__manage_space__screen_title)
        }

        listOf(
            viewModel.flowOfDeleteUnusedMediaSize to R.string.pref_delete_media_key,
            viewModel.flowOfDeleteCollectionSize to R.string.pref_delete_collection_key,
            viewModel.flowOfDeleteEverythingSize to R.string.pref_delete_everything_key,
            viewModel.flowOfDeleteBackupsSize to R.string.pref_delete_backups_key,
        ).forEach { (flowOfSize, key) ->
            val preference = requirePreference<TextWidgetPreference>(key)
            flowOfSize.collectInViewLifecycle { size -> preference.setWidgetTextBy(size) }
        }
    }

    /************************************ Delete unused media *************************************/

    private suspend fun onDeleteUnusedMediaClick() {
        val size = viewModel.flowOfDeleteUnusedMediaSize.value
        if (size is Size.FilesAndBytes) {
            val unusedFiles = size.files
            val unusedFileNames = unusedFiles.map { it.name }

            val deleteFilesPromptResult =
                requireContext().awaitDialog {
                    setTitle(CommonString.dialog__delete_unused_media_files__title)
                    setMultiChoiceItems(unusedFileNames, CheckedItems.All)
                    setPositiveButton(CommonString.dialog_positive_delete)
                    setNegativeButton(CommonString.dialog_cancel)
                }

            if (deleteFilesPromptResult is DialogResult.Ok.MultipleChoice) {
                val checkedItems = deleteFilesPromptResult.checkedItems
                val filesNamesToDelete = unusedFileNames.filterIndexed { index, _ -> checkedItems[index] }

                viewModel.deleteMediaFiles(filesNamesToDelete)
            }
        } else {
            showSnackbarIfCalculatingOrError(size)
        }
    }

    /*************************************** Delete backups ***************************************/

    private suspend fun onDeleteBackupsClick() {
        val size = viewModel.flowOfDeleteBackupsSize.value
        if (size is Size.FilesAndBytes) {
            val formatter = LocalizedUnambiguousBackupTimeFormatter()
            val backupFiles = size.files
            val backupNames = backupFiles.map { formatter.getTimeOfBackupAsText(it) }

            val chooseBackupsPromptResult =
                requireContext().awaitDialog {
                    setTitle(CommonString.dialog__delete_backups__title)
                    setMultiChoiceItems(backupNames, CheckedItems.None)
                    setPositiveButton(CommonString.dialog_positive_delete)
                    setNegativeButton(CommonString.dialog_cancel)
                }

            if (chooseBackupsPromptResult is DialogResult.Ok.MultipleChoice) {
                val checkedItems = chooseBackupsPromptResult.checkedItems
                val backupsToDelete = backupFiles.filterIndexed { index, _ -> checkedItems[index] }

                viewModel.deleteBackups(backupsToDelete)
            }
        } else {
            showSnackbarIfCalculatingOrError(size)
        }
    }

    // ************************************ Delete collection *************************************

    /** While we are in this activity, we may have other activities opened.
     * These activities might be visible to user, e.g. when using Split screen mode.
     * When the collection is deleted, whatever these activities are showing may become invalid.
     * To fix that, ideally we should refresh or finish these activities,
     * however, as this task is not trivial, we opt for killing the process instead and exit the application.
     * One major downside here is that killing the process makes this activity,
     * and possibly even the settings app, vanish without animation. **/
    private suspend fun onDeleteCollectionClick() {
        val size = viewModel.flowOfDeleteCollectionSize.value
        if (size is Size.Bytes) {
            val deleteCollectionPromptResult =
                requireContext().awaitDialog {
                    setTitle(CommonString.dialog__delete_collection__title)
                    setMessage(CommonString.dialog__delete_collection__message)
                    setPositiveButton(CommonString.dialog_positive_delete)
                    setNegativeButton(CommonString.dialog_cancel)
                }

            if (deleteCollectionPromptResult is DialogResult.Ok) {
                try {
                    viewModel.deleteCollection()
                } finally {
                    exitProcess(0)
                }
            }
        } else {
            showSnackbarIfCalculatingOrError(size)
        }
    }

    /************************************* Delete everything **************************************/

    @StringRes private var deleteEverythingDialogTitle: Int = 0

    @StringRes private var deleteEverythingDialogMessage: Int = 0

    private fun adjustDeleteEverythingStringsDependingOnCollectionLocation(preference: Preference) {
        if (viewModel.collectionDirectory.isInsideDirectoriesRemovedWithTheApp(requireContext())) {
            preference.setTitle(CommonString.pref__delete_everything__title)
            preference.setSummary(CommonString.pref__delete_everything__summary)
            deleteEverythingDialogTitle = CommonString.dialog__delete_everything__title
            deleteEverythingDialogMessage = CommonString.dialog__delete_everything__message
        } else {
            preference.setTitle(CommonString.pref__delete_app_data__title)
            preference.setSummary(CommonString.pref__delete_app_data__summary)
            deleteEverythingDialogTitle = CommonString.dialog__delete_app_data__title
            deleteEverythingDialogMessage = CommonString.dialog__delete_app_data__message
        }
    }

    private suspend fun onDeleteEverythingClick() {
        val deleteEverythingPromptResult =
            requireContext().awaitDialog {
                setTitle(deleteEverythingDialogTitle)
                setMessage(deleteEverythingDialogMessage)
                setPositiveButton(CommonString.dialog_positive_delete)
                setNegativeButton(CommonString.dialog_cancel)
            }

        if (deleteEverythingPromptResult is DialogResult.Ok) {
            viewModel.deleteEverything()
        }
    }

    /**********************************************************************************************
     ************************************* Misplaced methods **************************************
     **********************************************************************************************/

    // TODO Android N and earlier, formatFileSize & formatShortFileSize use powers of 1024.
    //   Perhaps correct input so that powers of 1000 are used on every API level?
    private fun TextWidgetPreference.setWidgetTextBy(size: Size) {
        fun Long.toHumanReadableSize() = Formatter.formatShortFileSize(requireContext(), this)

        widgetText =
            when (size) {
                is Size.Calculating -> getString(CommonString.pref__widget_text__calculating)
                is Size.Error -> getString(size.widgetTextId)
                is Size.Bytes -> size.totalSize.toHumanReadableSize()
                is Size.FilesAndBytes ->
                    resources.getQuantityString(
                        CommonPlurals.pref__widget_text__n_files_n_bytes,
                        size.files.size,
                        size.files.size,
                        size.totalSize.toHumanReadableSize(),
                    )
            }

        isEnabled =
            !(
                (size is Size.Bytes && size.totalSize == 0L) ||
                    (size is Size.FilesAndBytes && size.files.isEmpty())
            )
    }

    private fun Preference.launchOnPreferenceClick(block: suspend CoroutineScope.() -> Unit) {
        setOnPreferenceClickListener {
            launchCatchingTask { block() }
            true
        }
    }

    private fun showSnackbarIfCalculatingOrError(size: Size) {
        when (size) {
            is Size.Calculating -> showSnackbar(CommonString.pref__etc__snackbar__calculating)
            is Size.Error -> showSnackbar(requireContext().getUserFriendlyErrorText(size.exception))
            else -> {}
        }
    }
}

@get:StringRes
private val ManageSpaceProgress.messageRes: Int
    get() =
        when (this) {
            ManageSpaceProgress.DELETING_MEDIA -> CommonString.delete_media_message
            ManageSpaceProgress.DELETING_BACKUPS -> CommonString.progress__deleting_backups
            ManageSpaceProgress.DELETING_COLLECTION -> CommonString.progress__deleting_collection
        }
