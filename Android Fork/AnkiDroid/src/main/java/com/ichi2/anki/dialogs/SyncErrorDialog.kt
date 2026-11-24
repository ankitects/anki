// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Message
import android.provider.Settings
import androidx.annotation.CheckResult
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.ConflictResolution
import com.ichi2.anki.R
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_COLLECTION_TOO_LARGE
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_CONNECTION_ERROR
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_MEDIA_SYNC_ERROR
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_SYNC_BASIC_CHECK_ERROR
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_SYNC_CLOCK_OFF
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_SYNC_CONFLICT_RESOLUTION
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_SYNC_CORRUPT_COLLECTION
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_SYNC_SANITY_ERROR
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_USER_NOT_LOGGED_IN_SYNC
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.openUrl
import com.ichi2.utils.positiveButton
import com.ichi2.utils.setupEnterKeyHandler
import com.ichi2.utils.titleWithHelpIcon
import timber.log.Timber
import java.text.DateFormat

class SyncErrorDialog : AsyncDialogFragment() {
    interface SyncErrorDialogListener {
        fun showSyncErrorDialog(dialogType: Type)

        fun showSyncErrorDialog(
            dialogType: Type,
            message: String?,
        )

        fun loginToSyncServer()

        fun sync(conflict: ConflictResolution? = null)

        fun mediaCheck()

        fun integrityCheck()
    }

    fun requireSyncErrorDialogListener() = activity as SyncErrorDialogListener

    /** The type of the sync error dialog*/
    private val dialogType: Type
        get() = Type.fromCode(requireArguments().getInt(ARG_DIALOG_TYPE))

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog =
            AlertDialog
                .Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
        return when (dialogType) {
            DIALOG_USER_NOT_LOGGED_IN_SYNC -> {
                // User not logged in; take them to login screen
                dialog
                    .setIcon(R.drawable.ic_sync_problem)
                    .setPositiveButton(TR.sentenceCase.logIn) { _, _ ->
                        requireSyncErrorDialogListener().loginToSyncServer()
                    }.setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
                    .create()
            }
            DIALOG_CONNECTION_ERROR -> {
                // Connection error; allow user to retry or cancel
                dialog
                    .setIcon(R.drawable.ic_sync_problem)
                    .setPositiveButton(CommonString.retry) { _, _ ->
                        syncAndDismissAllDialogFragments()
                    }.setNegativeButton(CommonString.dialog_cancel) { _, _ ->
                        activity?.dismissAllDialogFragments()
                    }.create()
            }
            DIALOG_SYNC_CONFLICT_RESOLUTION -> {
                // Sync conflict; allow user to cancel, or choose between local and remote versions
                dialog
                    .titleWithHelpIcon(
                        text = getString(CommonString.sync_conflict_title_new),
                        startIcon = R.drawable.ic_sync_problem,
                    ) {
                        requireContext().openUrl(getString(R.string.link_sync_conflict_help))
                    }.setPositiveButton(CommonString.sync_conflict_keep_local_new) { _, _ ->
                        requireSyncErrorDialogListener().showSyncErrorDialog(DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL)
                    }.setNegativeButton(CommonString.sync_conflict_keep_remote_new) { _, _ ->
                        requireSyncErrorDialogListener().showSyncErrorDialog(DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE)
                    }.setNeutralButton(CommonString.dialog_cancel) { _, _ ->
                        activity?.dismissAllDialogFragments()
                    }.create()
            }
            DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL -> {
                // Confirmation before pushing local collection to server after sync conflict
                dialog
                    .setIcon(R.drawable.ic_sync_problem)
                    .setPositiveButton(CommonString.dialog_positive_replace) { _, _ ->
                        syncAndDismissAllDialogFragments(ConflictResolution.FULL_UPLOAD)
                    }.setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
                    .create()
            }
            DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE -> {
                // Confirmation before overwriting local collection with server collection after sync conflict
                dialog
                    .setIcon(R.drawable.ic_sync_problem)
                    .setPositiveButton(CommonString.dialog_positive_replace) { _, _ ->
                        syncAndDismissAllDialogFragments(ConflictResolution.FULL_DOWNLOAD)
                    }.setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
                    .create()
            }
            DIALOG_SYNC_SANITY_ERROR -> {
                // Sync sanity check error; allow user to cancel, or choose between local and remote versions
                dialog
                    .setPositiveButton(CommonString.sync_sanity_local) { _, _ ->
                        requireSyncErrorDialogListener().showSyncErrorDialog(DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL)
                    }.setNeutralButton(CommonString.sync_sanity_remote) { _, _ ->
                        requireSyncErrorDialogListener().showSyncErrorDialog(DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE)
                    }.setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
                    .create()
            }
            DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL -> {
                // Confirmation before pushing local collection to server after sanity check error
                dialog
                    .setPositiveButton(CommonString.dialog_positive_replace) { _, _ ->
                        syncAndDismissAllDialogFragments(ConflictResolution.FULL_UPLOAD)
                    }.setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
                    .create()
            }
            DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE -> {
                // Confirmation before overwriting local collection with server collection after sanity check error
                dialog
                    .setPositiveButton(CommonString.dialog_positive_replace) { _, _ ->
                        syncAndDismissAllDialogFragments(ConflictResolution.FULL_DOWNLOAD)
                    }.setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
                    .create()
            }
            DIALOG_MEDIA_SYNC_ERROR -> {
                dialog
                    .setPositiveButton(TR.sentenceCase.checkMediaAction) { _, _ ->
                        requireSyncErrorDialogListener().mediaCheck()
                        activity?.dismissAllDialogFragments()
                    }.setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
                    .create()
            }
            DIALOG_SYNC_CORRUPT_COLLECTION -> {
                isCancelable = false
                dialog
                    .setPositiveButton(CommonString.dialog_ok) { _, _ -> }
                    .setNegativeButton(CommonString.help) { _, _ ->
                        requireContext().openUrl(R.string.repair_deck)
                    }.create()
            }
            DIALOG_SYNC_BASIC_CHECK_ERROR -> {
                dialog
                    .setPositiveButton(TR.sentenceCase.checkDatabase) { _, _ ->
                        requireSyncErrorDialogListener().integrityCheck()
                        activity?.dismissAllDialogFragments()
                    }.setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
                    .create()
            }
            DIALOG_SYNC_CLOCK_OFF -> {
                dialog
                    .setPositiveButton(CommonString.open_settings, null)
                    .setNegativeButton(CommonString.dialog_cancel, null)
                    .create()
                    .apply {
                        // Override the default handler so the dialog remains open if no date-settings activity exists.
                        setOnShowListener {
                            positiveButton.setOnClickListener {
                                try {
                                    startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
                                    this@SyncErrorDialog.dismiss()
                                } catch (e: ActivityNotFoundException) {
                                    Timber.w(e, "No app can show date and time settings")
                                }
                            }
                        }
                        setupEnterKeyHandler()
                    }
            }
            DIALOG_COLLECTION_TOO_LARGE -> {
                dialog
                    .setPositiveButton(TR.sentenceCase.checkDatabase) { _, _ ->
                        requireSyncErrorDialogListener().integrityCheck()
                        activity?.dismissAllDialogFragments()
                    }.setNegativeButton(CommonString.dialog_cancel) { _, _ -> }
                    .create()
            }
        }
    }

    private val title: String
        get() =
            when (dialogType) {
                DIALOG_USER_NOT_LOGGED_IN_SYNC -> res().getString(CommonString.not_logged_in_title)
                DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL, DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE ->
                    res().getString(
                        CommonString.sync_conflict_replace_title,
                    )
                DIALOG_SYNC_CONFLICT_RESOLUTION -> res().getString(CommonString.sync_conflict_title_new)
                DIALOG_CONNECTION_ERROR,
                DIALOG_SYNC_SANITY_ERROR,
                DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL,
                DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE,
                DIALOG_MEDIA_SYNC_ERROR,
                DIALOG_SYNC_CORRUPT_COLLECTION,
                DIALOG_SYNC_BASIC_CHECK_ERROR,
                DIALOG_COLLECTION_TOO_LARGE,
                -> res().getString(CommonString.sync_error)
                DIALOG_SYNC_CLOCK_OFF -> res().getString(CommonString.vague_error)
            }

    /**
     * Get the title which is shown in notification bar when dialog fragment can't be shown
     *
     * @return tile to be shown in notification in bar
     */
    override val notificationTitle: String
        get() {
            return if (dialogType == DIALOG_USER_NOT_LOGGED_IN_SYNC) {
                res().getString(CommonString.sync_error)
            } else {
                title
            }
        }

    private val message: String?
        get() =
            when (dialogType) {
                DIALOG_USER_NOT_LOGGED_IN_SYNC -> res().getString(CommonString.login_create_account_message)
                DIALOG_CONNECTION_ERROR -> res().getString(CommonString.connection_error_message)
                DIALOG_SYNC_CONFLICT_RESOLUTION -> res().getString(CommonString.sync_conflict_message_new)
                DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL, DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL ->
                    res().getString(
                        CommonString.sync_conflict_local_confirm_new,
                    )
                DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE, DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE ->
                    res().getString(
                        CommonString.sync_conflict_remote_confirm_new,
                    )
                DIALOG_SYNC_CORRUPT_COLLECTION -> {
                    val syncMessage = requireArguments().getString(ARG_DIALOG_MESSAGE)
                    val repairUrl = res().getString(R.string.repair_deck)
                    val dialogMessage = res().getString(CommonString.sync_corrupt_database, repairUrl)
                    joinSyncMessages(dialogMessage, syncMessage)
                }
                DIALOG_SYNC_CLOCK_OFF ->
                    res().getString(
                        CommonString.sync_clock_off_with_current_time,
                        requireArguments().getString(ARG_DIALOG_MESSAGE).orEmpty(),
                        DateFormat.getDateTimeInstance().format(TimeManager.time.currentDate),
                    )
                else -> requireArguments().getString(ARG_DIALOG_MESSAGE)
            }

    private fun joinSyncMessages(
        dialogMessage: String?,
        syncMessage: String?,
    ): String? {
        // If both strings have text, separate them by a new line, otherwise return whichever has text
        return if (!dialogMessage.isNullOrEmpty() && !syncMessage.isNullOrEmpty()) {
            """
            $dialogMessage
            
            $syncMessage
            """.trimIndent()
        } else if (!dialogMessage.isNullOrEmpty()) {
            dialogMessage
        } else {
            syncMessage
        }
    }

    /**
     * Get the message which is shown in notification bar when dialog fragment can't be shown
     *
     * @return message to be shown in notification in bar
     */
    override val notificationMessage: String?
        get() {
            return if (dialogType == DIALOG_USER_NOT_LOGGED_IN_SYNC) {
                res().getString(CommonString.not_logged_in_title)
            } else {
                message
            }
        }

    override val dialogHandlerMessage: SyncErrorDialogMessageHandler
        get() {
            val dialogMessage = requireArguments().getString(ARG_DIALOG_MESSAGE)
            return SyncErrorDialogMessageHandler(dialogType, dialogMessage)
        }

    /**
     * Syncs with [conflictResolution] then dismisses all dialog fragments.
     */
    fun syncAndDismissAllDialogFragments(conflictResolution: ConflictResolution? = null) {
        requireSyncErrorDialogListener().sync(conflictResolution)
        activity?.dismissAllDialogFragments()
    }

    enum class Type(
        val code: Int,
    ) {
        DIALOG_USER_NOT_LOGGED_IN_SYNC(0),
        DIALOG_CONNECTION_ERROR(1),
        DIALOG_SYNC_CONFLICT_RESOLUTION(2),
        DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL(3),
        DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE(4),
        DIALOG_SYNC_SANITY_ERROR(5),
        DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL(6),
        DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE(7),
        DIALOG_MEDIA_SYNC_ERROR(8),
        DIALOG_SYNC_CORRUPT_COLLECTION(9),
        DIALOG_SYNC_BASIC_CHECK_ERROR(10),
        DIALOG_SYNC_CLOCK_OFF(11),
        DIALOG_COLLECTION_TOO_LARGE(12),
        ;

        companion object {
            fun fromCode(code: Int) = Type.entries.first { code == it.code }
        }
    }

    companion object {
        /**
         * Key for the ordinal of the sync error in Type
         */
        const val ARG_DIALOG_TYPE = "dialogType"

        /**
         * Key for the message to display in the dialog
         */
        const val ARG_DIALOG_MESSAGE = "dialogMessage"

        /**
         * A set of dialogs belonging to AnkiActivity which deal with sync problems
         *
         * @param dialogType An integer which specifies which of the sub-dialogs to show
         * @param dialogMessage A string which can be optionally used to set the dialog message
         */
        @CheckResult
        fun newInstance(
            dialogType: Type,
            dialogMessage: String?,
        ) = SyncErrorDialog().apply {
            arguments =
                Bundle().apply {
                    putInt(ARG_DIALOG_TYPE, dialogType.code)
                    putString(ARG_DIALOG_MESSAGE, dialogMessage)
                }
        }
    }

    class SyncErrorDialogMessageHandler(
        val dialogType: Type,
        val dialogMessage: String?,
    ) : DialogHandlerMessage(WhichDialogHandler.MSG_SHOW_SYNC_ERROR_DIALOG, "SyncErrorDialog") {
        override fun handleAsyncMessage(activity: AnkiActivity) {
            // we may be called via any AnkiActivity but media check is a DeckPicker thing
            activity
                .requireDeckPickerOrShowError()
                ?.showSyncErrorDialog(dialogType, dialogMessage)
        }

        override fun toMessage(): Message =
            Message.obtain().apply {
                what = this@SyncErrorDialogMessageHandler.what
                data =
                    Bundle().apply {
                        putInt(ARG_DIALOG_TYPE, dialogType.code)
                        putString(ARG_DIALOG_MESSAGE, dialogMessage)
                    }
            }

        companion object {
            fun fromMessage(message: Message): SyncErrorDialogMessageHandler {
                val dialogType = Type.fromCode(message.data.getInt(ARG_DIALOG_TYPE))
                val dialogMessage = message.data.getString(ARG_DIALOG_MESSAGE)
                return SyncErrorDialogMessageHandler(dialogType, dialogMessage)
            }
        }
    }
}
