// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.anki.compat.PackageInfoFlagsCompat
import com.ichi2.anki.isLoggedIn
import com.ichi2.anki.millisecondsSinceLastSync
import com.ichi2.anki.servicelayer.ScopedStorageService.collectionWillBeMadeInaccessibleAfterUninstall
import com.ichi2.anki.servicelayer.ScopedStorageService.userIsPromptedToDeleteCollectionOnUninstall
import com.ichi2.utils.Permissions
import com.ichi2.utils.cancelable
import com.ichi2.utils.checkBoxPrompt
import com.ichi2.utils.create
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import timber.log.Timber

/**
 * Prompts a user to backup via either sync or 'export collection'
 *
 * If dismissed, it will not appear for a period of time (~2 weeks): [calculateNextTimeToShowDialog]
 * After 2 dismissals, the user may hide the dialog permanently.
 *
 * This exists to inform the user their data is at risk when in a scoped folder.
 *
 * See [shouldShowDialog] for the criteria to display the dialog
 */
class BackupPromptDialog private constructor(
    private val windowContext: Context,
) {
    private lateinit var alertDialog: AlertDialog

    /**
     * After 2 dismissals, allow ignoring
     * Note: this is 0-based - the dialog has not been dismissed on the first viewing
     */
    private val allowUserToPermanentlyDismissDialog: Boolean
        get() = timesDialogDismissed > 1

    /** Whether the user has selected 'don't show again' */
    private var userCheckedDoNotShowAgain = false

    private var timesDialogDismissed: Int
        get() = windowContext.sharedPrefs().getInt("backupPromptDismissedCount", 0)
        set(value) =
            windowContext
                .sharedPrefs()
                .edit { putInt("backupPromptDismissedCount", value) }

    private var dialogPermanentlyDismissed: Boolean
        get() = windowContext.sharedPrefs().getBoolean("backupPromptDisabled", false)
        set(disablePermanently) {
            windowContext.sharedPrefs().edit {
                putBoolean("backupPromptDisabled", disablePermanently)
                if (disablePermanently) {
                    remove("backupPromptDismissedCount")
                    remove("timeToShowBackupDialog")
                }
            }
        }

    private var nextTimeToShowDialog: Long
        get() = windowContext.sharedPrefs().getLong("timeToShowBackupDialog", 0)
        set(value) {
            windowContext
                .sharedPrefs()
                .edit { putLong("timeToShowBackupDialog", value) }
        }

    private fun onDismiss() {
        Timber.i("BackupPromptDialog dismissed")
        if (userCheckedDoNotShowAgain) {
            showPermanentlyDismissDialog(
                this.windowContext,
                onCancel = {
                    userCheckedDoNotShowAgain = false
                    onDismiss()
                },
                onDisableReminder = { dialogPermanentlyDismissed = true },
            )
        } else {
            timesDialogDismissed += 1
            nextTimeToShowDialog = calculateNextTimeToShowDialog()
        }
    }

    private fun onBackup() {
        nextTimeToShowDialog = calculateNextTimeToShowDialog()
    }

    private fun calculateNextTimeToShowDialog(): Long {
        val now = TimeManager.time.intTimeMS()
        val fixedDayCount = 12
        val oneToFourDays = (1..4).random() // 13-16 days
        return now + (fixedDayCount + oneToFourDays) * ONE_DAY_IN_MS
    }

    private fun build(
        isLoggedIn: Boolean,
        performBackup: () -> Unit,
    ) {
        this.alertDialog =
            AlertDialog.Builder(windowContext).create {
                setIcon(if (isLoggedIn) R.drawable.ic_baseline_backup_24 else R.drawable.ic_backup_restore)
                title(R.string.backup_your_collection)
                message(R.string.backup_collection_message)
                positiveButton(if (isLoggedIn) R.string.button_sync else R.string.button_backup) {
                    Timber.i("User selected 'backup'")
                    onBackup()
                    performBackup()
                }
                if (allowUserToPermanentlyDismissDialog) {
                    checkBoxPrompt(R.string.button_do_not_show_again, isCheckedDefault = false) { checked ->
                        Timber.d("Don't show again checked: %b", checked)
                        userCheckedDoNotShowAgain = checked
                        alertDialog.positiveButton.isEnabled = !checked
                    }
                }
                negativeButton(R.string.button_backup_later) { onDismiss() }
                cancelable(false)
            }
    }

    companion object {
        private const val ONE_DAY_IN_MS = 1000 * 60 * 60 * 24

        /** @return Whether the dialog was shown */
        suspend fun showIfAvailable(deckPicker: DeckPicker): Boolean {
            val backupPrompt = BackupPromptDialog(deckPicker)

            if (!backupPrompt.shouldShowDialog()) {
                return false
            }
            val isLoggedIn = isLoggedIn()
            backupPrompt.apply {
                build(isLoggedIn) {
                    if (isLoggedIn) {
                        deckPicker.sync(conflict = null)
                    } else {
                        deckPicker.exportCollection()
                    }
                }
                alertDialog.show()
            }
            return true
        }

        /**
         *
         * @return A confirmation message to show the user before 'don't show this again' takes place
         * `null` if a confirmation dialog is not required and the dialog will be dismissed permanently without confirmation
         */
        @StringRes
        fun getPermanentlyDismissDialogMessageOrImmediatelyDismiss(context: Context): Int? {
            if (userIsPromptedToDeleteCollectionOnUninstall(context)) {
                Timber.d("User's collection may be deleted on uninstall")
                return R.string.dismiss_backup_warning_new_user // message stating collection will be deleted (new user/migrated)
            }

            // Full build users/users on old Androids will see this dialog, but only if they are syncing
            // It's much safer to ignore this. We'd like users to sync, but we shouldn't nag  if they don't consent
            if (Permissions.canManageExternalStorage(context)) {
                Timber.d("User is on a 'full' build. Disabling backup reminder without confirmation")
                return null
            }

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                // It is an assumption that Q will always handle android.R.attr.preserveLegacyExternalStorage
                // If we are incorrect, a user doesn't see a confirmation dialog
                // AND the user would be able to regain access with a 'full' build.
                Timber.d("User can regain access to their collection. Disabling backup reminder without confirmation")
                return null
            }

            // Given the assumptions above, this conditional should return true
            return if (collectionWillBeMadeInaccessibleAfterUninstall(context)) {
                Timber.d("User will lose access to their collection")
                // message stating collection will be made inaccessible
                // (existing user, not migrated)
                R.string.dismiss_backup_warning_upgrade
            } else {
                // A user is on a Play Store Build. They are on a version of Android with storage restrictions
                // Their collection is in a 'legacy' location but they are not going to lose access to their collection when they uninstall
                // The user is very likely syncing
                Timber.w("getPermanentlyDismissDialogMessage: unexpected state")
                CrashReportService.sendExceptionReport(IllegalStateException("unexpected state"), "getPermanentlyDismissDialogMessage")
                // assume this is a mistake and show a scary confirmation prompt
                R.string.dismiss_backup_warning_new_user // message stating collection will be deleted
            }
        }

        /** Explains to the user they should sync/backup as they risk to have data deleted or inaccessible (depending on whether legacy storage permission is kept) */
        fun showPermanentlyDismissDialog(
            context: Context,
            onCancel: () -> Unit,
            onDisableReminder: () -> Unit,
        ) {
            val message = getPermanentlyDismissDialogMessageOrImmediatelyDismiss(context)
            if (message == null) {
                Timber.i("permanently disabling 'Backup Prompt' reminder - no confirmation")
                onDisableReminder()
                return
            }

            AlertDialog.Builder(context).show {
                title(R.string.dismiss_backup_warning_title)
                message(message)
                setIcon(R.drawable.ic_warning)
                positiveButton(R.string.dialog_cancel) { onCancel() }
                negativeButton(R.string.button_disable_reminder) { onDisableReminder() }
            }
        }
    }

    private suspend fun shouldShowDialog(): Boolean = !userIsNewToAnkiDroid() && canProvideBackupOption() && timeToShowDialogAgain()

    /**
     * Whether:
     * * The user can sync, and we want to encourage them to sync regularly
     * * The user will lose/have their data deleted if
     *
     * @return `true` if user syncs; `true` for Play Store builds >= Android 11 (lose storage access on uninstall)
     * `false` if the user is not syncing and will not lose storage access on uninstall
     */
    private fun canProvideBackupOption(): Boolean {
        // If we are on a 'full' build, the user can always restore access to their collection.
        // But we want them to sync regularly as a backup
        if (isLoggedIn()) {
            // Show dialog to sync if user hasn't synced in a while
            return millisecondsSinceLastSync() >= ONE_DAY_IN_MS * 7
        }

        // Android proposes the user deletes non-legacy locations on uninstall
        if (userIsPromptedToDeleteCollectionOnUninstall(windowContext)) {
            Timber.v("Collection may be removed on uninstall")
            return true
        }

        // The user may have upgraded, in which it's unsafe to uninstall as Android
        // will permanently revoke access to the legacy folder
        // The collection won't be lost, but it will be inaccessible to a Play Store build
        return collectionWillBeMadeInaccessibleAfterUninstall(windowContext)
    }

    private fun timeToShowDialogAgain(): Boolean = !dialogPermanentlyDismissed && nextTimeToShowDialog <= TimeManager.time.intTimeMS()

    private suspend fun userIsNewToAnkiDroid(): Boolean {
        // A user is new if the app was installed > 7 days ago  OR if they have no cards
        val firstInstallTime = getFirstInstallTime() ?: 0
        if (TimeManager.time.intTimeMS() - firstInstallTime >= ONE_DAY_IN_MS * 7) {
            return false
        }

        // if for some reason the user has no cards after 7 days, don't bother
        return withCol {
            cardCount() == 0
        }
    }

    /** The time at which the app was first installed. Units are as per [System.currentTimeMillis()]. */
    private fun getFirstInstallTime(): Long? {
        return try {
            return windowContext.packageManager
                .getPackageInfoCompat(
                    windowContext.packageName,
                    PackageInfoFlagsCompat.of(0),
                )?.firstInstallTime
        } catch (exception: Exception) {
            Timber.w("failed to get first install time")
            null
        }
    }
}
