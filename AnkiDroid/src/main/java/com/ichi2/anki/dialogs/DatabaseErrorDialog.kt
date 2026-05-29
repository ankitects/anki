/*
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.os.Message
import android.os.Parcelable
import androidx.activity.addCallback
import androidx.annotation.CheckResult
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.BackupManager
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.ConflictResolution
import com.ichi2.anki.DatabaseRestorationListener
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.FatalInitializationError.StorageError
import com.ichi2.anki.InitialActivity.StartupFailure.InitializationError
import com.ichi2.anki.LocalizedUnambiguousBackupTimeFormatter
import com.ichi2.anki.R
import com.ichi2.anki.ankiActivity
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_CONFIRM_RESTORE_BACKUP
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_DB_ERROR
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_DB_LOCKED
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_DISK_FULL
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_ERROR_HANDLING
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_LOAD_FAILED
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_NEW_COLLECTION
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_ONE_WAY_SYNC_FROM_SERVER
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_REPAIR_COLLECTION
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_RESTORE_BACKUP
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType.INCOMPATIBLE_DB_VERSION
import com.ichi2.anki.dialogs.DatabaseErrorDialog.UninstallListItem.Companion.createList
import com.ichi2.anki.dialogs.ImportFileSelectionFragment.ImportOptions
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.anki.isLoggedIn
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.requireAnkiActivity
import com.ichi2.anki.servicelayer.DebugInfoService
import com.ichi2.anki.showImportDialog
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.ui.internationalization.toSentenceCase
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.utils.UiUtil.makeBold
import com.ichi2.utils.cancelable
import com.ichi2.utils.copyToClipboard
import com.ichi2.utils.create
import com.ichi2.utils.listItems
import com.ichi2.utils.listItemsAndMessage
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.io.File
import java.io.IOException

class DatabaseErrorDialog : AsyncDialogFragment() {
    private lateinit var backups: Array<File>

    private fun requireDeckPicker() = activity as DeckPicker

    @SuppressLint("CheckResult")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        super.onCreateDialog(savedInstanceState)
        val res = res()
        val alertDialog = AlertDialog.Builder(requireActivity())
        val isLoggedIn = isLoggedIn()
        alertDialog
            .cancelable(true)
            .title(text = title)
        var sqliteInstalled = false
        try {
            sqliteInstalled = Runtime.getRuntime().exec("sqlite3 --version").waitFor() == 0
        } catch (e: IOException) {
            Timber.i("sqlite3 not installed: ${e.message}")
        } catch (e: InterruptedException) {
            Timber.i("test for sqlite3 failed: ${e.message}")
        }
        return when (requireDialogType()) {
            DIALOG_LOAD_FAILED -> {
                // Collection failed to load; give user the option of either choosing from repair options, or closing
                // the activity
                alertDialog.show {
                    title(R.string.open_collection_failed_title)
                    cancelable(false)
                    message(text = message)
                    setIcon(R.drawable.ic_warning)
                    positiveButton(R.string.error_handling_options) {
                        showDatabaseErrorDialog(DIALOG_ERROR_HANDLING)
                    }
                    negativeButton(R.string.close) {
                        closeCollectionAndFinish()
                    }
                }
            }
            DIALOG_DB_ERROR -> {
                // Database Check failed to execute successfully; give user the option of either choosing from repair
                // options, submitting an error report, or closing the activity
                alertDialog
                    .create {
                        cancelable(false)
                        title(R.string.answering_error_title)
                        message(text = message)
                        setIcon(R.drawable.ic_warning)
                        positiveButton(R.string.error_handling_options) {
                            showDatabaseErrorDialog(DIALOG_ERROR_HANDLING)
                        }
                        negativeButton(R.string.answering_error_report) {
                            requireDeckPicker().sendErrorReport()
                            requireActivity().dismissAllDialogFragments()
                        }
                        neutralButton(R.string.close) {
                            closeCollectionAndFinish()
                        }
                    }.apply {
                        show()
                        getButton(Dialog.BUTTON_NEGATIVE).isEnabled = requireDeckPicker().hasErrorFiles()
                    }
            }
            DIALOG_ERROR_HANDLING -> {
                // The user has asked to see repair options; allow them to choose one of the repair options or go back
                // to the previous dialog
                val options = ArrayList<String>(8)
                val values = ArrayList<ErrorHandlingEntries>(8)
                if (!requireAnkiActivity().colIsOpenUnsafe()) {
                    // retry
                    options.add(res.getString(R.string.backup_retry_opening))
                    values.add(ErrorHandlingEntries.RETRY)
                }
                // repair db with sqlite
                if (sqliteInstalled) {
                    options.add(res.getString(R.string.backup_error_menu_repair))
                    values.add(ErrorHandlingEntries.REPAIR)
                }
                // // restore from backup
                options.add(res.getString(R.string.backup_restore))
                values.add(ErrorHandlingEntries.RESTORE)
                // one-way sync from server
                if (isLoggedIn) {
                    options.add(res.getString(R.string.backup_one_way_sync_from_server))
                    values.add(ErrorHandlingEntries.ONE_WAY)
                }

                val activity = requireActivity()
                val shouldOfferResetToDefaultDirectory =
                    try {
                        val currentDir = CollectionHelper.getCurrentAnkiDroidDirectory(activity)
                        val defaultDir = CollectionHelper.getDefaultAnkiDroidDirectory(activity)
                        currentDir.absolutePath != defaultDir.absolutePath
                    } catch (e: Throwable) {
                        Timber.w(e, "Failed to determine whether to offer reset-to-default directory option")
                        false
                    }

                if (shouldOfferResetToDefaultDirectory) {
                    options.add(res.getString(R.string.backup_use_default_folder))
                    values.add(ErrorHandlingEntries.RESET_TO_DEFAULT_DIRECTORY)
                }

                // delete old collection and build new one
                options.add(res.getString(R.string.backup_del_collection))
                values.add(ErrorHandlingEntries.NEW)
                // copy stack trace and debug info
                options.add(TR.sentenceCase.copyDebugInfo)
                values.add(ErrorHandlingEntries.DEBUG_INFO)

                alertDialog.show {
                    title(R.string.error_handling_title)
                    setIcon(R.drawable.ic_warning)
                    negativeButton(R.string.dialog_cancel)
                    listItems(items = options) { _, index ->
                        when (values[index]) {
                            ErrorHandlingEntries.RETRY -> {
                                ActivityCompat.recreate(requireActivity())
                                return@listItems
                            }
                            ErrorHandlingEntries.REPAIR -> {
                                showDatabaseErrorDialog(DIALOG_REPAIR_COLLECTION)
                                return@listItems
                            }
                            ErrorHandlingEntries.RESTORE -> {
                                showDatabaseErrorDialog(DIALOG_RESTORE_BACKUP)
                                return@listItems
                            }
                            ErrorHandlingEntries.ONE_WAY -> {
                                showDatabaseErrorDialog(DIALOG_ONE_WAY_SYNC_FROM_SERVER)
                                return@listItems
                            }
                            ErrorHandlingEntries.RESET_TO_DEFAULT_DIRECTORY -> {
                                try {
                                    val defaultDir = CollectionHelper.getDefaultAnkiDroidDirectory(activity)
                                    CollectionManager.closeCollectionBlocking()
                                    CollectionHelper.resetAnkiDroidDirectory(activity, defaultDir)
                                    closeCollectionAndFinish()
                                } catch (e: Throwable) {
                                    Timber.w(e, "Failed to reset AnkiDroid directory to default")
                                    showDatabaseErrorDialog(DIALOG_LOAD_FAILED)
                                }
                                return@listItems
                            }
                            ErrorHandlingEntries.NEW -> {
                                showDatabaseErrorDialog(DIALOG_NEW_COLLECTION)
                                return@listItems
                            }

                            ErrorHandlingEntries.DEBUG_INFO -> {
                                lifecycleScope.launch {
                                    // Using NonCancellable to ensure that debug information collection completes even if the coroutine is canceled
                                    withContext(NonCancellable) {
                                        copyStackTraceAndDebugInfo()
                                    }
                                }
                                return@listItems
                            }
                        }
                    }
                }
            }
            DIALOG_REPAIR_COLLECTION -> {
                // Allow user to run BackupManager.repairCollection()
                alertDialog.show {
                    title(R.string.dialog_positive_repair)
                    message(text = message)
                    setIcon(R.drawable.ic_warning)
                    positiveButton(R.string.dialog_positive_repair) {
                        requireDeckPicker().repairCollection()
                        requireActivity().dismissAllDialogFragments()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_RESTORE_BACKUP -> {
                // Allow user to restore one of the backups
                backups = BackupManager.getBackups(CollectionHelper.getCollectionPath(requireContext()))
                if (backups.isEmpty()) {
                    alertDialog
                        .title(R.string.backup_restore)
                        .title(text = message)
                        .positiveButton(R.string.dialog_ok) {
                            showDatabaseErrorDialog(DIALOG_ERROR_HANDLING)
                        }
                } else {
                    // Show backups sorted with latest on top
                    backups.reverse()
                    val formatter = LocalizedUnambiguousBackupTimeFormatter()
                    val dates = backups.map { formatter.getTimeOfBackupAsText(it) }

                    alertDialog
                        .title(R.string.backup_restore_select_title)
                        .positiveButton(R.string.restore_backup_choose_another) {
                            ankiActivity?.let {
                                ImportFileSelectionFragment.openImportFilePicker(it, ImportFileSelectionFragment.ImportFileType.APKG)
                            }
                        }.negativeButton(R.string.dialog_cancel)
                        .setSingleChoiceItems(dates.toTypedArray(), -1) { _, index: Int ->
                            if (backups[index].length() > 0) {
                                // restore the backup if it's valid
                                requireDeckPicker().restoreFromBackup(
                                    backups[index].path,
                                )
                                requireActivity().dismissAllDialogFragments()
                            } else {
                                // otherwise show an error dialog
                                AlertDialog.Builder(requireActivity()).show {
                                    title(R.string.vague_error)
                                    message(R.string.backup_invalid_file_error)
                                    positiveButton(R.string.dialog_ok)
                                }
                            }
                        }
                }
                alertDialog.create()
            }
            DIALOG_NEW_COLLECTION -> {
                // Allow user to create a new empty collection
                alertDialog.show {
                    title(R.string.backup_new_collection)
                    message(text = message)
                    positiveButton(R.string.dialog_positive_create) {
                        val time = TimeManager.time
                        Timber.i(
                            "closeCollection: %s",
                            "DatabaseErrorDialog: Before Create New Collection",
                        )
                        CollectionManager.closeCollectionBlocking()
                        val path1 = CollectionHelper.getCollectionPath(requireActivity())
                        if (BackupManager.moveDatabaseToBrokenDirectory(path1, false, time)) {
                            ActivityCompat.recreate(requireDeckPicker())
                        } else {
                            showDatabaseErrorDialog(DIALOG_LOAD_FAILED)
                        }
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_CONFIRM_DATABASE_CHECK -> {
                // Confirmation dialog for database check
                alertDialog.show {
                    title(text = TR.sentenceCase.checkDatabase)
                    message(text = message)
                    positiveButton(R.string.dialog_ok) {
                        requireDeckPicker().integrityCheck()
                        requireActivity().dismissAllDialogFragments()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_CONFIRM_RESTORE_BACKUP -> {
                // Confirmation dialog for backup restore
                alertDialog.show {
                    title(R.string.restore_backup_title)
                    message(text = message)
                    positiveButton(R.string.dialog_continue) {
                        showDatabaseErrorDialog(DIALOG_RESTORE_BACKUP)
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_ONE_WAY_SYNC_FROM_SERVER -> {
                // Allow user to do a full-sync from the server
                alertDialog.show {
                    title(R.string.backup_one_way_sync_from_server)
                    message(text = message)
                    positiveButton(R.string.dialog_positive_overwrite) {
                        requireDeckPicker().sync(ConflictResolution.FULL_DOWNLOAD)
                        requireActivity().dismissAllDialogFragments()
                    }
                    negativeButton(R.string.dialog_cancel)
                }
            }
            DIALOG_DB_LOCKED -> {
                // If the database is locked, all we can do is ask the user to exit.
                alertDialog.show {
                    title(R.string.database_locked_title)
                    message(text = message)
                    positiveButton(R.string.close) {
                        closeCollectionAndFinish()
                    }
                    cancelable(false)
                }
            }
            INCOMPATIBLE_DB_VERSION -> {
                val values: MutableList<IncompatibleDbVersionEntries> = ArrayList(2)
                val options = mutableListOf<CharSequence>()
                options.add(makeBold(res.getString(R.string.backup_restore)))
                values.add(IncompatibleDbVersionEntries.RESTORE)
                if (isLoggedIn) {
                    options.add(makeBold(res.getString(R.string.backup_one_way_sync_from_server)))
                    values.add(IncompatibleDbVersionEntries.ONE_WAY)
                }
                alertDialog.show {
                    cancelable(false)
                    title(R.string.incompatible_database_version_title)
                    setIcon(R.drawable.ic_warning)
                    positiveButton(R.string.close) {
                        closeCollectionAndFinish()
                    }
                    listItemsAndMessage(message = message, items = options) { _, index: Int ->
                        when (values[index]) {
                            IncompatibleDbVersionEntries.RESTORE ->
                                showDatabaseErrorDialog(
                                    DIALOG_RESTORE_BACKUP,
                                )
                            IncompatibleDbVersionEntries.ONE_WAY ->
                                showDatabaseErrorDialog(
                                    DIALOG_ONE_WAY_SYNC_FROM_SERVER,
                                )
                        }
                    }
                }
            }
            DIALOG_DISK_FULL -> {
                alertDialog.show {
                    title(R.string.storage_full_title)
                    message(text = message)
                    positiveButton(R.string.close) {
                        closeCollectionAndFinish()
                    }
                }
            }
            DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL -> {
                val listItems = UninstallListItem.createList()
                alertDialog.show {
                    title(R.string.directory_inaccessible_after_uninstall)
                    listItemsAndMessage(message = message, items = listItems.map { getString(it.stringRes) }) { _, index: Int ->
                        val listItem = listItems[index]
                        listItem.onClick(requireAnkiActivity())
                        if (listItem.dismissesDialog) {
                            dismiss()
                        }
                    }
                    cancelable(false)
                }
            }
        }
    }

    override fun setupDialog(
        dialog: Dialog,
        style: Int,
    ) {
        super.setupDialog(dialog, style)

        if (requireDialogType() == DIALOG_RESTORE_BACKUP) {
            // we don't want to go back to DIALOG_CONFIRM_RESTORE_BACKUP if back is pressed
            // instead, close all dialogs and return to the DeckPicker
            (dialog as AlertDialog).onBackPressedDispatcher.addCallback(this, true) {
                Timber.i("DIALOG_RESTORE_BACKUP caught hardware back button")
                requireActivity().dismissAllDialogFragments()
            }
        }
    }

    /** @see DeckPicker.showDatabaseErrorDialog */
    private fun showDatabaseErrorDialog(errorDialogType: DatabaseErrorDialogType) {
        requireDeckPicker().showDatabaseErrorDialog(errorDialogType, exceptionData)
    }

    /**
     * Collects the current exception's stack trace and debug information,
     * and copies the combines information to the Android clipboard.
     */
    private suspend fun copyStackTraceAndDebugInfo() {
        val context = getSafeContext()
        val combinedInfo =
            listOfNotNull(
                exceptionData?.toHumanReadableString(),
                DebugInfoService.getDebugInfo(context),
            ).joinToString(separator = "\n")

        context.copyToClipboard(
            combinedInfo,
            failureMessageId = R.string.about_ankidroid_error_copy_debug_info,
        )
    }

    /** List items for [DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL] */
    enum class UninstallListItem(
        @StringRes val stringRes: Int,
        val dismissesDialog: Boolean,
        val onClick: (AnkiActivity) -> Unit,
    ) {
        RESTORE_FROM_ANKIWEB(
            R.string.restore_data_from_ankiweb,
            dismissesDialog = true,
            {
                this.displayCreateNewCollectionDialog(it)
            },
        ),
        INSTALL_NON_PLAY_APP_RECOMMENDED(
            R.string.install_non_play_store_ankidroid_recommended,
            dismissesDialog = false,
            {
                it.openUrl(R.string.link_install_non_play_store_install)
            },
        ),
        INSTALL_NON_PLAY_APP_NORMAL(
            R.string.install_non_play_store_ankidroid,
            dismissesDialog = false,
            {
                it.openUrl(R.string.link_install_non_play_store_install)
            },
        ),
        RESTORE_FROM_BACKUP(
            R.string.restore_data_from_backup,
            dismissesDialog = false,
            { activity ->
                Timber.i("Restoring from colpkg")
                val newAnkiDroidDirectory = CollectionHelper.getDefaultAnkiDroidDirectory(activity)
                activity.importColpkgListener = DatabaseRestorationListener(activity, newAnkiDroidDirectory)

                activity.launchCatchingTask {
                    CollectionHelper.ankiDroidDirectoryOverride = newAnkiDroidDirectory

                    CollectionManager.withCol {
                        activity.showImportDialog(
                            ImportOptions(
                                importTextFile = false,
                                importColpkg = true,
                                importApkg = false,
                            ),
                        )
                    }
                }
            },
        ),
        GET_HELP(
            R.string.help_title_get_help,
            dismissesDialog = false,
            {
                it.openUrl(R.string.link_forum)
            },
        ),
        RECREATE_COLLECTION(
            R.string.create_new_collection,
            dismissesDialog = false,
            {
                this.displayCreateNewCollectionDialog(it)
            },
        ),
        ;

        companion object {
            /** A dialog which creates a new collection in an unsafe location */
            fun displayCreateNewCollectionDialog(context: AnkiActivity) {
                val directory =
                    try {
                        CollectionHelper.getDefaultAnkiDroidDirectory(context)
                    } catch (e: SystemStorageException) {
                        Timber.w(e, "failed to show 'Create new collection' dialog")
                        FatalErrorDialog.build(context, InitializationError(StorageError(e))).show()
                        return
                    }
                AlertDialog.Builder(context).show {
                    title(R.string.backup_new_collection)
                    setIcon(R.drawable.ic_warning)
                    message(R.string.new_unsafe_collection)
                    positiveButton(R.string.dialog_positive_create) {
                        Timber.w("Creating new collection")
                        Timber.i(
                            "closeCollection: %s",
                            "DatabaseErrorDialog: Before Create New Collection",
                        )
                        CollectionManager.closeCollectionBlocking()
                        CollectionHelper.resetAnkiDroidDirectory(context, directory)
                        context.closeCollectionAndFinish()
                    }
                    negativeButton(R.string.dialog_cancel)
                    cancelable(false)
                }
            }

            /**
             * List of options to present to the user when the collection is broken.
             */
            fun createList() =
                if (isLoggedIn()) {
                    listOf(RESTORE_FROM_ANKIWEB, INSTALL_NON_PLAY_APP_NORMAL, RESTORE_FROM_BACKUP, GET_HELP, RECREATE_COLLECTION)
                } else {
                    listOf(INSTALL_NON_PLAY_APP_RECOMMENDED, RESTORE_FROM_BACKUP, GET_HELP, RECREATE_COLLECTION)
                }

            /**
             * List of options to present to the users when the storage is not usable.
             * Copied from [createList], without [RESTORE_FROM_ANKIWEB]. Indeed, if we're unable to
             * access the Deck Picker to sync, we have no folder to restore to.
             */
            fun createNoStorageList() = createList().filter { it != RESTORE_FROM_ANKIWEB }
        }
    }

    private fun closeCollectionAndFinish() {
        requireAnkiActivity().closeCollectionAndFinish()
    } // Generic message shown when a libanki task failed

    // The sqlite database has been corrupted (DatabaseErrorHandler.onCorrupt() was called)
    // Show a specific message appropriate for the situation
    private val message: String?
        get() =
            when (requireDialogType()) {
                DIALOG_LOAD_FAILED ->
                    if (databaseCorruptFlag) {
                        // The sqlite database has been corrupted (DatabaseErrorHandler.onCorrupt() was called)
                        // Show a specific message appropriate for the situation
                        res().getString(R.string.corrupt_db_message, res().getString(R.string.repair_deck))
                    } else {
                        // Generic message shown when a libanki task failed
                        res().getString(R.string.access_collection_failed_message, res().getString(R.string.link_help))
                    }
                DIALOG_DB_ERROR -> res().getString(R.string.answering_error_message)
                DIALOG_DISK_FULL -> res().getString(R.string.storage_full_message)
                DIALOG_REPAIR_COLLECTION -> res().getString(R.string.repair_deck_dialog, BackupManager.BROKEN_COLLECTIONS_SUFFIX)
                DIALOG_RESTORE_BACKUP -> res().getString(R.string.backup_restore_no_backups)
                DIALOG_NEW_COLLECTION -> res().getString(R.string.backup_del_collection_question)
                DIALOG_CONFIRM_DATABASE_CHECK -> res().getString(R.string.check_db_warning)
                DIALOG_CONFIRM_RESTORE_BACKUP -> res().getString(R.string.restore_backup)
                DIALOG_ONE_WAY_SYNC_FROM_SERVER -> res().getString(R.string.backup_full_sync_from_server_question)
                DIALOG_DB_LOCKED -> res().getString(R.string.database_locked_summary)
                INCOMPATIBLE_DB_VERSION -> {
                    var databaseVersion = -1
                    try {
                        databaseVersion = CollectionHelper.getDatabaseVersion(requireContext())
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to get database version, using -1")
                    }
                    val schemaVersion = Consts.BACKEND_SCHEMA_VERSION
                    res().getString(
                        R.string.incompatible_database_version_summary,
                        schemaVersion,
                        databaseVersion,
                    )
                }
                DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL -> {
                    val directory =
                        context?.let { CollectionHelper.getCurrentAnkiDroidDirectory(it) }
                            ?: res().getString(R.string.card_browser_unknown_deck_name)
                    res().getString(R.string.directory_inaccessible_after_uninstall_summary, directory)
                }
                DIALOG_ERROR_HANDLING -> requireArguments().getString("dialogMessage")
            }
    private val title: String
        get() =
            when (requireDialogType()) {
                DIALOG_LOAD_FAILED -> res().getString(R.string.open_collection_failed_title)
                DIALOG_ERROR_HANDLING -> res().getString(R.string.error_handling_title)
                DIALOG_REPAIR_COLLECTION -> res().getString(R.string.dialog_positive_repair)
                DIALOG_RESTORE_BACKUP -> res().getString(R.string.backup_restore)
                DIALOG_NEW_COLLECTION -> res().getString(R.string.backup_new_collection)
                DIALOG_CONFIRM_DATABASE_CHECK -> TR.databaseCheckTitle().toSentenceCase(res(), R.string.sentence_check_db)
                DIALOG_CONFIRM_RESTORE_BACKUP -> res().getString(R.string.restore_backup_title)
                DIALOG_ONE_WAY_SYNC_FROM_SERVER -> res().getString(R.string.backup_one_way_sync_from_server)
                DIALOG_DB_LOCKED -> res().getString(R.string.database_locked_title)
                INCOMPATIBLE_DB_VERSION -> res().getString(R.string.incompatible_database_version_title)
                DIALOG_DB_ERROR -> res().getString(R.string.answering_error_title)
                DIALOG_DISK_FULL -> res().getString(R.string.storage_full_title)
                DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL -> res().getString(R.string.directory_inaccessible_after_uninstall)
            }

    override val notificationMessage: String? get() = message
    override val notificationTitle: String get() = res().getString(R.string.answering_error_title)

    override val dialogHandlerMessage
        get() = ShowDatabaseErrorDialog(requireDialogType())

    private fun requireDialogType() = BundleCompat.getParcelable(requireArguments(), ARG_DIALOG, DatabaseErrorDialogType::class.java)!!

    private val exceptionData: CustomExceptionData? by lazy {
        BundleCompat.getParcelable(requireArguments(), ARG_EXCEPTION, CustomExceptionData::class.java)
    }

    @Parcelize
    enum class DatabaseErrorDialogType : Parcelable {
        DIALOG_LOAD_FAILED,
        DIALOG_DB_ERROR,
        DIALOG_ERROR_HANDLING,
        DIALOG_REPAIR_COLLECTION,
        DIALOG_RESTORE_BACKUP,
        DIALOG_NEW_COLLECTION,
        DIALOG_CONFIRM_DATABASE_CHECK,
        DIALOG_CONFIRM_RESTORE_BACKUP,
        DIALOG_ONE_WAY_SYNC_FROM_SERVER,

        /** If the database is locked, all we can do is reset the app  */
        DIALOG_DB_LOCKED,

        /** If the database is at a version higher than what we can currently handle  */
        INCOMPATIBLE_DB_VERSION,

        /** If the disk space is full **/
        DIALOG_DISK_FULL,

        /** If [android.R.attr.preserveLegacyExternalStorage] is no longer active */
        DIALOG_STORAGE_UNAVAILABLE_AFTER_UNINSTALL,
    }

    companion object {
        // public flag which lets us distinguish between inaccessible and corrupt database
        var databaseCorruptFlag = false

        /**
         * Key for passing a CustomExceptionData object in a Bundle,
         * contains error message and stack trace.
         *
         * @see CustomExceptionData
         */
        private const val ARG_EXCEPTION = "exception"

        // Key for the dialog type in the Bundle, indicating which dialog to show
        private const val ARG_DIALOG = "dialog"

        /**
         * A set of dialogs which deal with problems with the database when it can't load
         *
         * @param dialogType the sub-dialog to show
         */
        @CheckResult
        fun newInstance(
            dialogType: DatabaseErrorDialogType,
            exceptionData: CustomExceptionData? = null,
        ): DatabaseErrorDialog {
            val f = DatabaseErrorDialog()
            val args = Bundle()
            args.putParcelable(ARG_DIALOG, dialogType)
            exceptionData?.let { args.putParcelable(ARG_EXCEPTION, it) }
            f.arguments = args
            return f
        }
    }

    /** Database error dialog */
    class ShowDatabaseErrorDialog(
        val dialogType: DatabaseErrorDialogType,
    ) : DialogHandlerMessage(
            which = WhichDialogHandler.MSG_SHOW_DATABASE_ERROR_DIALOG,
            analyticName = "DatabaseErrorDialog",
        ) {
        override fun handleAsyncMessage(activity: AnkiActivity) {
            activity.showDatabaseErrorDialog(dialogType)
        }

        override fun toMessage(): Message =
            Message.obtain().apply {
                what = this@ShowDatabaseErrorDialog.what
                data =
                    bundleOf(
                        ARG_DIALOG to dialogType,
                    )
            }

        companion object {
            fun fromMessage(message: Message): ShowDatabaseErrorDialog {
                val dialogType = BundleCompat.getParcelable(message.data, ARG_DIALOG, DatabaseErrorDialogType::class.java)!!
                return ShowDatabaseErrorDialog(dialogType)
            }
        }
    }

    @Parcelize
    class CustomExceptionData(
        val stackTrace: String,
    ) : Parcelable {
        fun toHumanReadableString() = stackTrace

        companion object {
            private const val MAX_STACKTRACE_LINES = 2000

            @CheckResult
            fun fromException(exception: Exception): CustomExceptionData {
                val stackTraceLines = exception.stackTraceToString().lines()
                val truncatedStackTrace =
                    if (stackTraceLines.size > MAX_STACKTRACE_LINES) {
                        stackTraceLines.take(MAX_STACKTRACE_LINES).joinToString("\n") + "\n...<stack trace truncated>...\n"
                    } else {
                        stackTraceLines.joinToString("\n")
                    }

                return CustomExceptionData(truncatedStackTrace)
            }
        }
    }
}

private enum class ErrorHandlingEntries {
    RETRY,
    REPAIR,
    RESTORE,
    ONE_WAY,
    RESET_TO_DEFAULT_DIRECTORY,
    NEW,
    DEBUG_INFO,
}

private enum class IncompatibleDbVersionEntries {
    RESTORE,
    ONE_WAY,
}
