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

import android.app.Dialog
import android.os.Bundle
import android.os.Message
import androidx.annotation.CheckResult
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.ConflictResolution
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_CONNECTION_ERROR
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_MEDIA_SYNC_ERROR
import com.ichi2.anki.dialogs.SyncErrorDialog.Type.DIALOG_SYNC_BASIC_CHECK_ERROR
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
import com.ichi2.utils.titleWithHelpIcon

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
        get() = Type.fromCode(requireArguments().getInt(SYNC_ERROR_DIALOG_TYPE_KEY))

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
                    }.setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                    .create()
            }
            DIALOG_CONNECTION_ERROR -> {
                // Connection error; allow user to retry or cancel
                dialog
                    .setIcon(R.drawable.ic_sync_problem)
                    .setPositiveButton(R.string.retry) { _, _ ->
                        syncAndDismissAllDialogFragments()
                    }.setNegativeButton(R.string.dialog_cancel) { _, _ ->
                        activity?.dismissAllDialogFragments()
                    }.create()
            }
            DIALOG_SYNC_CONFLICT_RESOLUTION -> {
                // Sync conflict; allow user to cancel, or choose between local and remote versions
                dialog
                    .titleWithHelpIcon(
                        text = getString(R.string.sync_conflict_title_new),
                        startIcon = R.drawable.ic_sync_problem,
                    ) {
                        requireContext().openUrl(getString(R.string.link_sync_conflict_help))
                    }.setPositiveButton(R.string.sync_conflict_keep_local_new) { _, _ ->
                        requireSyncErrorDialogListener().showSyncErrorDialog(DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL)
                    }.setNegativeButton(R.string.sync_conflict_keep_remote_new) { _, _ ->
                        requireSyncErrorDialogListener().showSyncErrorDialog(DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE)
                    }.setNeutralButton(R.string.dialog_cancel) { _, _ ->
                        activity?.dismissAllDialogFragments()
                    }.create()
            }
            DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL -> {
                // Confirmation before pushing local collection to server after sync conflict
                dialog
                    .setIcon(R.drawable.ic_sync_problem)
                    .setPositiveButton(R.string.dialog_positive_replace) { _, _ ->
                        syncAndDismissAllDialogFragments(ConflictResolution.FULL_UPLOAD)
                    }.setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                    .create()
            }
            DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE -> {
                // Confirmation before overwriting local collection with server collection after sync conflict
                dialog
                    .setIcon(R.drawable.ic_sync_problem)
                    .setPositiveButton(R.string.dialog_positive_replace) { _, _ ->
                        syncAndDismissAllDialogFragments(ConflictResolution.FULL_DOWNLOAD)
                    }.setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                    .create()
            }
            DIALOG_SYNC_SANITY_ERROR -> {
                // Sync sanity check error; allow user to cancel, or choose between local and remote versions
                dialog
                    .setPositiveButton(R.string.sync_sanity_local) { _, _ ->
                        requireSyncErrorDialogListener().showSyncErrorDialog(DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL)
                    }.setNeutralButton(R.string.sync_sanity_remote) { _, _ ->
                        requireSyncErrorDialogListener().showSyncErrorDialog(DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE)
                    }.setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                    .create()
            }
            DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL -> {
                // Confirmation before pushing local collection to server after sanity check error
                dialog
                    .setPositiveButton(R.string.dialog_positive_replace) { _, _ ->
                        syncAndDismissAllDialogFragments(ConflictResolution.FULL_UPLOAD)
                    }.setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                    .create()
            }
            DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE -> {
                // Confirmation before overwriting local collection with server collection after sanity check error
                dialog
                    .setPositiveButton(R.string.dialog_positive_replace) { _, _ ->
                        syncAndDismissAllDialogFragments(ConflictResolution.FULL_DOWNLOAD)
                    }.setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                    .create()
            }
            DIALOG_MEDIA_SYNC_ERROR -> {
                dialog
                    .setPositiveButton(TR.sentenceCase.checkMediaAction) { _, _ ->
                        requireSyncErrorDialogListener().mediaCheck()
                        activity?.dismissAllDialogFragments()
                    }.setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                    .create()
            }
            DIALOG_SYNC_CORRUPT_COLLECTION -> {
                dialog
                    .setPositiveButton(R.string.dialog_ok) { _, _ -> }
                    .setNegativeButton(R.string.help) { _, _ ->
                        requireContext().openUrl(R.string.repair_deck)
                    }.setCancelable(false)
                    .create()
            }
            DIALOG_SYNC_BASIC_CHECK_ERROR -> {
                dialog
                    .setPositiveButton(TR.sentenceCase.checkDatabase) { _, _ ->
                        requireSyncErrorDialogListener().integrityCheck()
                        activity?.dismissAllDialogFragments()
                    }.setNegativeButton(R.string.dialog_cancel) { _, _ -> }
                    .create()
            }
        }
    }

    private val title: String
        get() =
            when (dialogType) {
                DIALOG_USER_NOT_LOGGED_IN_SYNC -> res().getString(R.string.not_logged_in_title)
                DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL, DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE ->
                    res().getString(
                        R.string.sync_conflict_replace_title,
                    )
                DIALOG_SYNC_CONFLICT_RESOLUTION -> res().getString(R.string.sync_conflict_title_new)
                DIALOG_CONNECTION_ERROR,
                DIALOG_SYNC_SANITY_ERROR,
                DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL,
                DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE,
                DIALOG_MEDIA_SYNC_ERROR,
                DIALOG_SYNC_CORRUPT_COLLECTION,
                DIALOG_SYNC_BASIC_CHECK_ERROR,
                -> res().getString(R.string.sync_error)
            }

    /**
     * Get the title which is shown in notification bar when dialog fragment can't be shown
     *
     * @return tile to be shown in notification in bar
     */
    override val notificationTitle: String
        get() {
            return if (dialogType == DIALOG_USER_NOT_LOGGED_IN_SYNC) {
                res().getString(R.string.sync_error)
            } else {
                title
            }
        }

    private val message: String?
        get() =
            when (dialogType) {
                DIALOG_USER_NOT_LOGGED_IN_SYNC -> res().getString(R.string.login_create_account_message)
                DIALOG_CONNECTION_ERROR -> res().getString(R.string.connection_error_message)
                DIALOG_SYNC_CONFLICT_RESOLUTION -> res().getString(R.string.sync_conflict_message_new)
                DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_LOCAL, DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_LOCAL ->
                    res().getString(
                        R.string.sync_conflict_local_confirm_new,
                    )
                DIALOG_SYNC_CONFLICT_CONFIRM_KEEP_REMOTE, DIALOG_SYNC_SANITY_ERROR_CONFIRM_KEEP_REMOTE ->
                    res().getString(
                        R.string.sync_conflict_remote_confirm_new,
                    )
                DIALOG_SYNC_CORRUPT_COLLECTION -> {
                    val syncMessage = requireArguments().getString(DIALOG_MESSAGE_KEY)
                    val repairUrl = res().getString(R.string.repair_deck)
                    val dialogMessage = res().getString(R.string.sync_corrupt_database, repairUrl)
                    joinSyncMessages(dialogMessage, syncMessage)
                }
                else -> requireArguments().getString(DIALOG_MESSAGE_KEY)
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
                res().getString(R.string.not_logged_in_title)
            } else {
                message
            }
        }

    override val dialogHandlerMessage: SyncErrorDialogMessageHandler
        get() {
            val dialogMessage = requireArguments().getString(DIALOG_MESSAGE_KEY)
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
        ;

        companion object {
            fun fromCode(code: Int) = Type.entries.first { code == it.code }
        }
    }

    companion object {
        /**
         * Key for the ordinal of the sync error in Type
         */
        const val SYNC_ERROR_DIALOG_TYPE_KEY = "dialogType"

        /**
         * Key for the message to display in the dialog
         */
        const val DIALOG_MESSAGE_KEY = "dialogMessage"

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
                bundleOf(
                    SYNC_ERROR_DIALOG_TYPE_KEY to dialogType.code,
                    DIALOG_MESSAGE_KEY to dialogMessage,
                )
        }
    }

    class SyncErrorDialogMessageHandler(
        private val dialogType: Type,
        private val dialogMessage: String?,
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
                    bundleOf(
                        SYNC_ERROR_DIALOG_TYPE_KEY to dialogType,
                        DIALOG_MESSAGE_KEY to dialogMessage,
                    )
            }

        companion object {
            fun fromMessage(message: Message): SyncErrorDialogMessageHandler {
                val dialogType = Type.fromCode(message.data.getInt(SYNC_ERROR_DIALOG_TYPE_KEY))
                val dialogMessage = message.data.getString(DIALOG_MESSAGE_KEY)
                return SyncErrorDialogMessageHandler(dialogType, dialogMessage)
            }
        }
    }
}
