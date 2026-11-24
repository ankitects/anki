// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>

package com.ichi2.anki

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.net.Uri
import android.text.format.Formatter
import android.view.WindowManager.BadTokenException
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import anki.collection.Progress
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CrashReportData.Companion.throwIfDialogUnusable
import com.ichi2.anki.CrashReportData.Companion.toCrashReportData
import com.ichi2.anki.ankicommon.BuildConfig
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.android.AnkiBroadcastReceiver
import com.ichi2.anki.common.annotations.UseContextParameter
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.destinations.DeckOptionsDestination
import com.ichi2.anki.common.destinations.DeferredNavigation
import com.ichi2.anki.common.destinations.PreferencesDestination
import com.ichi2.anki.common.destinations.toIntent
import com.ichi2.anki.common.utils.android.isRobolectric
import com.ichi2.anki.dialogs.databaseErrorDialogs
import com.ichi2.anki.exception.CollectionLockedException
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.exception.StorageNotConfiguredException
import com.ichi2.anki.progress.blockTouches
import com.ichi2.anki.progress.unblockTouches
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.startup.redirectToMainEntryPoint
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.openUrl
import com.ichi2.utils.create
import com.ichi2.utils.message
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.setupEnterKeyHandler
import com.ichi2.utils.title
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.BackendException.BackendCardTypeException
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.exceptions.BackendInterruptedException
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException
import net.ankiweb.rsdroid.exceptions.BackendNetworkException
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import org.jetbrains.annotations.VisibleForTesting
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import com.ichi2.anki.common.android.R as CommonR

/** Overridable reference to [Dispatchers.IO]. Useful if tests can't use it */
// COULD_BE_BETTER: this shouldn't be necessary, but TestClass::runWith needs it
@VisibleForTesting
var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

/** Whether [showError] should throw an exception on failure */
@VisibleForTesting
var throwOnShowError = false

interface OnErrorListener {
    val onError: Channel<Exception>
}

fun <T, U> T.launchCatchingIO(block: suspend T.() -> U): Job where T : ViewModel, T : OnErrorListener = launchCatching(ioDispatcher, block)

fun <T, U> T.launchCatching(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend T.() -> U,
): Job where T : ViewModel, T : OnErrorListener =
    viewModelScope.launch(context) {
        try {
            block()
        } catch (e: Exception) {
            // propagate our own cancellation; others are errors
            if (e is CancellationException) ensureActive()
            Timber.w(e)
            onError.send(e)
            if (throwOnShowError) throw IllegalStateException("throwOnShowError: $e", e)
        }
    }

fun <T> ViewModel.launchCatchingIO(
    errorMessageHandler: suspend (String) -> Unit,
    block: suspend CoroutineScope.() -> T,
): Job = viewModelScope.launchCatching(ioDispatcher, errorMessageHandler, block)

fun <T> CoroutineScope.asyncIO(block: suspend CoroutineScope.() -> T): Deferred<T> = async(ioDispatcher, block = block)

fun <T> ViewModel.asyncIO(block: suspend CoroutineScope.() -> T): Deferred<T> = viewModelScope.asyncIO(block)

/**
 * Runs a suspend function that catches any uncaught errors and reports them to the user.
 * Errors from the backend contain localized text that is often suitable to show to the user as-is.
 * Other errors should ideally be handled in the block.
 *
 * TODO: `Throwable.getLocalizedMessage()` might be null, and `BackendException` constructor
 *   accepts a null message, so `exc.localizedMessage!!` is probably dangerous.
 *   If not, add a comment explaining why, or refactor to have a method that returns
 *   a non-null localized message.
 */
suspend fun <T> FragmentActivity.runCatching(
    errorMessage: String? = null,
    skipCrashReport: ((Exception) -> Boolean)? = null,
    block: suspend () -> T?,
): T? {
    // appends the pre-coroutine stack to the error message. Example:
    // at com.ichi2.anki.CoroutineHelpersKt.launchCatchingTask(CoroutineHelpers.kt:188)
    // at com.ichi2.anki.CoroutineHelpersKt.launchCatchingTask$default(CoroutineHelpers.kt:184)
    // at com.ichi2.anki.BackendBackupsKt.performBackupInBackground(BackendBackups.kt:26)
    //  This is only performed in DEBUG mode to reduce performance impact
    val callerTrace =
        if (BuildConfig.DEBUG) {
            Thread
                .currentThread()
                .stackTrace
                .drop(14)
                .joinToString(prefix = "\tat ", separator = "\n\tat ")
        } else {
            null
        }

    try {
        return block()
    } catch (exc: Exception) {
        if (skipCrashReport?.invoke(exc) == true) {
            Timber.i("Showing error dialog but not sending a crash report.")
            showError(exc.localizedMessage!!, exc.toCrashReportData(this, reportException = false))
            return null
        }
        if (exc is CancellationException) {
            throw exc // CancellationException should be re-thrown to propagate it to the parent coroutine
        }
        showException(exc, errorMessage, callerTrace)
    }
    return null
}

fun FragmentActivity.showException(
    exc: Exception,
    errorMessage: String? = null,
    callerTrace: String? = null,
) {
    when (exc) {
        is BackendInterruptedException -> {
            Timber.w(exc, errorMessage)
            exc.localizedMessage?.let { showSnackbar(it) }
        }
        is StorageNotConfiguredException -> {
            // expected before first-run setup completes: no crash report
            // edge case when 'ensureStorageIsReady' was insufficient
            Timber.w(exc, errorMessage)
            if (!isFinishing) redirectToMainEntryPoint()
        }
        is BackendNetworkException, is BackendSyncException, is StorageAccessException, is BackendCardTypeException,
        is CollectionLockedException,
        -> {
            // these exceptions do not generate worthwhile crash reports
            Timber.i("Showing error dialog but not sending a crash report.")
            showError(exc.localizedMessage!!, exc.toCrashReportData(this, reportException = false))
        }
        // must precede `is BackendException`: BackendDbCorruptException is a subclass
        is SQLiteDatabaseCorruptException, is BackendDbCorruptException -> {
            Timber.e(exc, errorMessage)
            DatabaseCorruption.isDetected = true
            if (callerTrace != null) Timber.e(callerTrace)
            databaseErrorDialogs.showLoadFailed(this, exc)
        }
        is BackendException -> {
            Timber.e(exc, errorMessage)
            if (callerTrace != null) Timber.e(callerTrace)
            showError(exc.localizedMessage!!, exc.toCrashReportData(this))
        }
        else -> {
            Timber.e(exc, errorMessage)
            if (callerTrace != null) Timber.e(callerTrace)
            showError(exc)
        }
    }
}

/**
 * Launch a job that catches any uncaught errors and reports them to the user.
 * Errors from the backend contain localized text that is often suitable to show to the user as-is.
 * Other errors should ideally be handled in the block.
 */
fun FragmentActivity.launchCatchingTask(
    errorMessage: String? = null,
    skipCrashReport: ((Exception) -> Boolean)? = null,
    block: suspend CoroutineScope.() -> Unit,
): Job =
    lifecycle.coroutineScope.launch {
        runCatching(errorMessage, skipCrashReport = skipCrashReport) { block() }
    }

context(activity: FragmentActivity)
fun Deferred<*>.awaitCatchingTask(): Job = activity.launchCatchingTask { await() }

/**
 * Launch a job that catches any uncaught errors and reports them to the user.
 * Errors from the backend contain localized text that is often suitable to show to the user as-is.
 * Other errors should ideally be handled in the block.
 */
fun <T> FragmentActivity.asyncCatching(
    errorMessage: String? = null,
    skipCrashReport: ((Exception) -> Boolean)? = null,
    block: suspend CoroutineScope.() -> T,
): Deferred<T?> =
    lifecycle.coroutineScope.async {
        runCatching(errorMessage, skipCrashReport = skipCrashReport) { block() }
    }

/**
 * Launch a job that catches any uncaught errors and reports them to the user.
 * Errors from the backend contain localized text that is often suitable to show to the user as-is.
 * Other errors should ideally be handled in the block.
 *
 * A valid, non-finishing activity must be used when running this.
 */
fun Fragment.launchCatchingTask(
    errorMessage: String? = null,
    skipCrashReport: ((Exception) -> Boolean)? = null,
    block: suspend CoroutineScope.() -> Unit,
): Job {
    val fragmentLifecycle = lifecycle
    return fragmentLifecycle.coroutineScope.launch {
        // Removal can precede scope registration; reading the fragment creates a new lifecycle.
        if (fragmentLifecycle.currentState == Lifecycle.State.DESTROYED) {
            Timber.w("Skipping launchCatchingTask for %s: fragment lifecycle is destroyed", this@launchCatchingTask.javaClass.simpleName)
            return@launch
        }
        val validActivity = validActivityForTask() ?: return@launch
        validActivity.runCatching(errorMessage, skipCrashReport = skipCrashReport) { block() }
    }
}

private fun Fragment.validActivityForTask(): FragmentActivity? =
    // It's a bug to call this if the activity is invalid or finishing.
    kotlin
        .runCatching {
            requireActivity(simpleName = true).also { activity ->
                check(!activity.isFinishing) {
                    "Cannot launch task for ${this@validActivityForTask.javaClass.simpleName}: activity is finishing"
                }
            }
        }.getOrElse {
            Timber.w(it, "Fragment.launchCatchingTask")
            if (isRobolectric) throw it
            null
        }

/**
 * [Fragment.requireActivity], with a short stable name so crash reports can be grouped together.
 *
 * ```diff
 * - Fragment <name>{afc1c3c} (f8db5cf7-13f5-495f-8195-79e0be3aff68) not attached to an activity.
 * + Fragment <name> not attached to an activity.
 * ```
 */
fun Fragment.requireActivity(simpleName: Boolean): FragmentActivity =
    if (simpleName) {
        checkNotNull(activity) { "Fragment ${javaClass.simpleName} not attached to an activity." }
    } else {
        requireActivity()
    }

/**
 * Like [Fragment.launchCatchingTask], but tied to the current view's lifecycle.
 * Skips [block] if the view is destroyed before the job starts.
 *
 * @return the launched job, or `null` if the fragment has no view.
 */
fun Fragment.launchCatchingViewTask(
    errorMessage: String? = null,
    skipCrashReport: ((Exception) -> Boolean)? = null,
    block: suspend CoroutineScope.() -> Unit,
): Job? {
    val owner = viewLifecycleOwnerLiveData.value ?: return null
    return owner.lifecycleScope.launch {
        // A synchronous replacement can destroy the view before lifecycleScope registers cancellation.
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            Timber.w(
                "Skipping launchCatchingViewTask for %s: view lifecycle is destroyed",
                this@launchCatchingViewTask.javaClass.simpleName,
            )
            return@launch
        }
        val validActivity = validActivityForTask() ?: return@launch
        validActivity.runCatching(errorMessage, skipCrashReport = skipCrashReport) { block() }
    }
}

/**
 * Displays an error dialog with title 'Error' and provided [message].
 * May report the error when the dialog is dismissed
 *
 * @param message Message to display to user
 * @param crashReportData Crash report data which may be reported when the dialog is dismissed.
 */
fun Context.showError(
    message: CharSequence,
    crashReportData: CrashReportData?,
) {
    showErrorDialog(message, crashReportData, onDismiss = null)
}

/** [showError], returning once the dialog is dismissed. Cancelling the caller dismisses it. */
suspend fun Context.showErrorAndAwaitDismissal(
    message: CharSequence,
    crashReportData: CrashReportData?,
): Unit =
    suspendCancellableCoroutine { continuation ->
        val dialog = showErrorDialog(message, crashReportData, onDismiss = { continuation.resume(Unit) })
        continuation.invokeOnCancellation { dialog?.dismiss() }
    }

/**
 * @param onDismiss called when the dialog is dismissed, or immediately if it can't be displayed
 * @return the displayed dialog, or `null` if it can't be displayed
 */
private fun Context.showErrorDialog(
    message: CharSequence,
    crashReportData: CrashReportData?,
    onDismiss: (() -> Unit)?,
): AlertDialog? {
    crashReportData.throwIfDialogUnusable(message.toString())

    Timber.i("Error dialog displayed")

    val helpAction = crashReportData?.helpAction?.takeIf { it.canExecute(this) }

    try {
        return AlertDialog
            .Builder(this)
            .create {
                title(CommonString.vague_error)
                message(text = message)
                positiveButton(CommonString.dialog_ok)
                helpAction?.let { neutralButton(text = it.buttonText(this@showErrorDialog)) }
                val reportOnDismiss = crashReportData?.takeIf { it.reportableException }
                if (reportOnDismiss != null) Timber.w("sending crash report on close")
                setOnDismissListener {
                    reportOnDismiss?.sendCrashReport()
                    onDismiss?.invoke()
                }
            }.apply {
                // setup the help link. Link is non-null if neutralButton exists.
                setOnShowListener {
                    neutralButton?.setOnClickListener {
                        lifecycle.coroutineScope.launch {
                            if (helpAction!!.execute(this@showErrorDialog)) dismiss()
                        }
                    }
                }
                setupEnterKeyHandler()
                show()
            }
    } catch (ex: BadTokenException) {
        // issue 12718: activity provided by `context` was not running
        Timber.w(ex, "unable to display error dialog")
        crashReportData?.sendCrashReport()
        onDismiss?.invoke()
        return null
    }
}

/** In most cases, you'll want [FragmentActivity.withProgress]
 * instead. This lower-level routine can be used to integrate your own
 * progress UI.
 */
suspend fun <T> Backend.withProgress(
    progressContext: ProgressContext,
    extractProgress: ProgressContext.() -> Unit,
    updateUi: ProgressContext.() -> Unit,
    block: suspend CoroutineScope.() -> T,
): T =
    coroutineScope {
        val monitor =
            launch {
                progressContext.monitorProgress(this@withProgress, extractProgress, updateUi)
            }
        try {
            block()
        } finally {
            monitor.cancel()
        }
    }

/**
 * Run the provided operation, showing a progress window until it completes.
 * Progress info is polled from the backend.
 *
 * Starts the progress dialog after 600ms so that quick operations don't just show
 * flashes of a dialog.
 */
suspend fun <T> FragmentActivity.withProgress(
    progressContext: ProgressContext = ProgressContext(),
    extractProgress: ProgressContext.() -> Unit,
    onCancel: ((Backend) -> Unit)? = { it.setWantsAbort() },
    @StringRes manualCancelButton: Int? = null,
    op: suspend () -> T,
): T {
    val backend = CollectionManager.getBackend()
    return withProgressDialog(
        context = this@withProgress,
        onCancel =
            if (onCancel != null) {
                fun() {
                    onCancel(backend)
                }
            } else {
                null
            },
        manualCancelButton = manualCancelButton,
    ) { dialog ->
        backend.withProgress(
            progressContext = progressContext,
            extractProgress = extractProgress,
            updateUi = { updateDialog(dialog) },
        ) {
            op()
        }
    }
}

/**
 * Run the provided operation, showing a progress window with the provided
 * message until the operation completes.
 *
 * Starts the progress dialog after 600ms so that quick operations don't just show
 * flashes of a dialog.
 */
suspend fun <T> Activity.withProgress(
    message: String = resources.getString(CommonString.dialog_processing),
    op: suspend () -> T,
): T =
    withProgressDialog(
        context = this@withProgress,
        onCancel = null,
    ) { dialog ->
        @Suppress("Deprecation") // ProgressDialog deprecation
        dialog.setMessage(message)
        op()
    }

/** @see withProgress(String, ...) */
suspend fun <T> Fragment.withProgress(
    message: String = getString(CommonString.dialog_processing),
    block: suspend () -> T,
): T = requireActivity().withProgress(message, block)

/** @see withProgress(String, ...) */
suspend fun <T> Activity.withProgress(
    @StringRes messageId: Int,
    block: suspend () -> T,
): T = withProgress(resources.getString(messageId), block)

/** @see withProgress(String, ...) */
suspend fun <T> Fragment.withProgress(
    @StringRes messageId: Int,
    block: suspend () -> T,
): T = requireActivity().withProgress(messageId, block)

/** Used to avoid showing extra progress dialogs when one already shown. */
private var progressDialogShown = false

// TODO: #17551 add a lease-owned progress UI, with `CollectionLease.cancel` as its Cancel handler.
//  Rethink `progressDialogShown` and `FLAG_NOT_TOUCHABLE`.
@Suppress("Deprecation") // ProgressDialog deprecation
suspend fun <T> withProgressDialog(
    context: Activity,
    onCancel: (() -> Unit)?,
    delayDuration: Duration = 600.milliseconds,
    @StringRes manualCancelButton: Int? = null,
    op: suspend (android.app.ProgressDialog) -> T,
): T =
    coroutineScope {
        val dialog =
            android.app.ProgressDialog(context, CommonR.style.AppCompatProgressDialogStyle).apply {
                setCancelable(onCancel != null)
                if (manualCancelButton != null) {
                    setCancelable(false)
                    setCanceledOnTouchOutside(false)
                    setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(manualCancelButton)) { _, _ ->
                        Timber.i("Progress dialog cancelled via cancel button")
                        onCancel?.let { it() }
                    }
                } else {
                    onCancel?.let {
                        setOnCancelListener {
                            Timber.i("Progress dialog cancelled via cancel listener")
                            it()
                        }
                    }
                }
            }
        // disable taps immediately
        context.runOnUiThread { context.window.blockTouches() }
        // reveal the dialog after 600ms
        var dialogIsOurs = false
        val dialogJob =
            launch {
                delay(delayDuration)
                if (!progressDialogShown) {
                    Timber.i(
                        """Displaying progress dialog: $delayDuration elapsed;
                |cancellable: ${onCancel != null}; 
                |manualCancel: ${manualCancelButton != null}
                |
                        """.trimMargin(),
                    )
                    dialog.show()
                    progressDialogShown = true
                    dialogIsOurs = true
                } else {
                    Timber.w(
                        """A progress dialog is already displayed, not displaying progress dialog: 
                |cancellable: ${onCancel != null}; 
                |manualCancel: ${manualCancelButton != null}
                |
                        """.trimMargin(),
                    )
                }
            }
        try {
            op(dialog)
        } finally {
            dialogJob.cancel()
            dismissDialogIfShowing(dialog)
            context.runOnUiThread { context.window.unblockTouches() }
            if (dialogIsOurs) {
                progressDialogShown = false
            }
        }
    }

private fun dismissDialogIfShowing(dialog: Dialog) {
    try {
        if (dialog.isShowing) {
            dialog.dismiss()
        }
    } catch (e: Exception) {
        Timber.w(e)
    }
}

/**
 * Poll the backend for progress info every 100ms until cancelled by caller.
 * Calls extractProgress() to gather progress info and write it into
 * [ProgressContext]. Calls updateUi() to update the UI with the extracted
 * progress.
 */
private suspend fun ProgressContext.monitorProgress(
    backend: Backend,
    extractProgress: ProgressContext.() -> Unit,
    updateUi: ProgressContext.() -> Unit,
) {
    val state = this
    while (true) {
        state.progress =
            withContext(Dispatchers.IO) {
                backend.latestProgress()
            }
        state.extractProgress()
        // on main thread, so op can update UI
        withContext(Dispatchers.Main) {
            state.updateUi()
        }
        delay(100.milliseconds)
    }
}

/**
 * Holds the current backend progress, and text/amount properties
 * that can be written to in order to update the UI.
 */
data class ProgressContext(
    var progress: Progress = Progress.getDefaultInstance(),
    var text: String? = null,
    /** If set, shows a progress bar with `current` of `max` complete. */
    var amount: Amount? = null,
    val formatAmount: (Amount) -> String = { (current, max) -> "$current/$max" },
    /** Separator between [text] and [amount] */
    val separator: String = " ",
) {
    @Suppress("Deprecation") // ProgressDialog deprecation
    fun updateDialog(dialog: android.app.ProgressDialog) {
        val message =
            listOfNotNull(
                text,
                amount?.let { formatAmount(it) },
            ).joinToString(separator)
        dialog.setMessage(message)
    }

    companion object {
        /**
         * A [com.ichi2.anki.ProgressContext] which formats progress as bytes:
         *
         * `28 MB/141 MB`
         */
        fun ofBytes(context: Context) =
            ProgressContext(
                formatAmount = { (current, max) ->
                    // replace spaces with NBSP so newlines are handled better
                    val curStr = Formatter.formatShortFileSize(context, current).replace(' ', '\u00A0')
                    val maxStr = Formatter.formatShortFileSize(context, max).replace(' ', '\u00A0')
                    context.getString(CommonString.progress_amount_bytes, curStr, maxStr)
                },
            )
    }

    /**
     * Represents a progress value and a maximum limit.
     *
     * @see ProgressContext
     */
    // values are 'Long' as this can represent bytes.
    data class Amount(
        val current: Long,
        val max: Long,
    )
}

/**
 * Ensures that current continuation is not [cancelled][CancellableContinuation.isCancelled].
 *
 * @throws [CancellationException] if canceled. This does not contain the original cancellation cause
*/
fun <T> CancellableContinuation<T>.ensureActive() {
    // we can't use .isActive here, or the exception would take precedence over a resumed exception
    if (isCancelled) throw CancellationException()
}

/**
 * Displays an error dialog with title 'Error' and [throwable] data.
 * May report the error when the dialog is dismissed
 *
 * @param throwable The exception to display to the user
 * @param reportException If `true`, report the exception when the dialog is dismissed
 */
private fun Activity.showError(
    throwable: Throwable,
    reportException: Boolean = true,
) = showError(throwable.toString(), throwable.toCrashReportData(context = this, reportException))

/**
 * Since AnkiBroadcastReceiver's `onReceiveBroadcast` methods is expected to finish quickly, this
 * helper function is required to run a suspending function from an `onReceiveBroadcast` method.
 * [AnkiBroadcastReceiver.goAsync] extends the lifetime of the `onReceiveBroadcast` method and tells
 * the OS not to kill the process prematurely.
 *
 * Theoretically, an expedited Worker could also be used to run a suspending function from `onReceiveBroadcast`.
 * However, AnkiDroid is only allotted a fixed number of expedited Worker calls per day
 * and those expedited calls are also used by the sync service, so it's best to conserve them.
 *
 * Do not call [AnkiBroadcastReceiver.goAsync] directly before calling this function.
 *
 * @param timeout Just in case the block hangs. Cannot exceed 8 seconds, because an ANR may occur if
 * an AnkiBroadcastReceiver's onReceiveBroadcast method runs for longer than 10 seconds.
 * See [the docs](https://developer.android.com/reference/android/content/BroadcastReceiver#goAsync()).
 * @param block The suspending function to run.
 *
 * @see AnkiBroadcastReceiver.goAsync
 * @see AnkiBroadcastReceiver.onReceiveBroadcast
 */
fun AnkiBroadcastReceiver.runGloballyWithTimeout(
    timeout: Duration,
    block: suspend () -> Unit,
) {
    val pendingResult = goAsync()
    if (pendingResult == null) {
        // pendingResult should never be null, so this should never happen.
        // According to the implementation of goAsync, if it is, that indicates goAsync was called twice for the same onReceiveBroadcast.
        Timber.w("goAsync returned null, cannot run block")
        CrashReportService.sendExceptionReport(
            message =
                "goAsync returned null for BroadcastReceiver: " +
                    "This should never happen and indicates goAsync was called twice for the same onReceiveBroadcast",
            origin = "CoroutineHelpers:BroadcastReceiver.runGloballyWithTimeout",
        )
        return
    }

    applicationScope.launch {
        try {
            withTimeout(minOf(timeout, 8.seconds)) {
                block()
            }
        } catch (e: TimeoutCancellationException) {
            Timber.w(e, "runGloballyWithTimeout timed out after $timeout")
        } finally {
            pendingResult.finish()
        }
    }
}

data class CrashReportData(
    val exception: Throwable,
    val origin: String,
    val reportableException: Boolean,
) {
    /**
     * Optional link to a help page regarding the error
     *
     * For example: https://docs.ankiweb.net/templates/errors.html#no-cloze-filter-on-cloze-notetype
     * Or opening the deck options
     */
    val helpAction: HelpAction?
        get() = HelpAction.from(exception)

    fun shouldReportException(): Boolean {
        if (!reportableException) return false
        if (exception is BackendNotFoundException) return false
        if (exception.isInvalidFsrsParametersException()) return false
        if (exception.isDeckNotFoundInLimitsMapException()) return false
        if (exception is BackendInvalidInputException && exception.message == "missing template") return false
        return true
    }

    fun sendCrashReport() {
        if (!shouldReportException()) {
            Timber.i("skipped crash report due to further validation")
            return
        }
        CrashReportService.sendExceptionReport(exception, origin)
    }

    /**
     * Optional action to provide more information about an error
     *
     * Examples:
     * - Open https://docs.ankiweb.net/templates/errors.html#no-cloze-filter-on-cloze-notetype
     * - Open the deck options
     */
    sealed class HelpAction {
        /** Label for the 'help' button on the error dialog. Defaults to "Help". */
        open fun buttonText(context: Context): CharSequence = context.getString(CommonString.help)

        /** `false` hides the help button. */
        open fun canExecute(context: Context): Boolean = true

        /** Perform the action. @return whether the error dialog should be dismissed. */
        abstract suspend fun execute(context: Context): Boolean

        data class AnkiBackendLink(
            val link: Uri,
        ) : HelpAction() {
            override suspend fun execute(context: Context): Boolean {
                context.openUrl(link)
                return false
            }
        }

        /** Open the deck options for the current deck */
        data object OpenDeckOptions : HelpAction() {
            override suspend fun execute(context: Context): Boolean {
                // if we're in the error dialog, we have no context of the deck which caused the exception
                // assume it's the current deck
                val openCurrentDeckOptions = DeckOptionsDestination.fromCurrentDeck()
                context.startActivity(with(DeferredNavigation) { openCurrentDeckOptions.toIntent() })
                // dismiss the dialog - the user should have resolved the issue
                return true
            }
        }

        /**
         * Opens settings at the Advanced screen, where changing the 'AnkiDroid directory'
         * resolves a [CollectionLockedException] (#21051)
         */
        data object OpenAdvancedSettings : HelpAction() {
            override fun buttonText(context: Context): CharSequence = context.getString(CommonString.settings)

            override suspend fun execute(context: Context): Boolean {
                Timber.i("Opening 'Advanced settings'")
                context.startActivity(with(DeferredNavigation) { PreferencesDestination.Advanced.toIntent() })
                return true
            }
        }

        /** Opens 'Check Database' */
        data object OpenCheckDatabase : HelpAction() {
            override fun buttonText(context: Context): CharSequence = with(context) { TR.sentenceCase.checkDatabase }

            override fun canExecute(context: Context): Boolean = databaseErrorDialogs.canConfirmDatabaseCheck(context)

            override suspend fun execute(context: Context): Boolean {
                Timber.i("Opening 'Check Database'")
                databaseErrorDialogs.confirmDatabaseCheck(context)
                return true
            }
        }

        companion object {
            fun from(e: Throwable): HelpAction? {
                val link =
                    try {
                        (e as? BackendException)
                            ?.getDesktopHelpPageLink(CollectionManager.getBackend())
                            ?.toUri()
                    } catch (e: Exception) {
                        Timber.w(e)
                        null
                    }

                if (link != null) return AnkiBackendLink(link)
                if (e is CollectionLockedException) return OpenAdvancedSettings
                if (e.isInvalidFsrsParametersException()) return OpenDeckOptions
                if (e.isDeckNotFoundInLimitsMapException()) return OpenCheckDatabase
                if (e is BackendNotFoundException) return OpenCheckDatabase

                return null
            }
        }
    }

    companion object {
        @UseContextParameter("context")
        fun Throwable.toCrashReportData(
            context: Context,
            reportException: Boolean = true,
        ) = CrashReportData(
            exception = this,
            // Appears as 'ManageNotetypes'
            origin = context::class.java.simpleName,
            reportableException = reportException,
        )

        /**
         * If [throwOnShowError] is set, throws the exception from the crash report data
         *
         * So unit tests can fail if an unexpected exception is thrown
         *
         * Note: this occurs regardless of the status of [reportableException]
         *
         * @param message The message of the thrown [IllegalStateException]
         * @throws IllegalStateException with [exception] as an innerException if the receiver
         *  is non-null
         */
        fun CrashReportData?.throwIfDialogUnusable(message: String) {
            if (!throwOnShowError) return
            val message = "throwOnShowError: $message"
            if (this == null) throw IllegalStateException(message)
            throw IllegalStateException(message, exception)
        }

        private fun Throwable.isInvalidFsrsParametersException(): Boolean =
            // `TR` may fail in an error-reporting context
            try {
                when (message) {
                    // https://github.com/ankitects/anki/blob/f3b4284afbb38b894164cd4de3e7b690f2bc62a5/rslib/src/scheduler/fsrs/error.rs#L18
                    "invalid params provided" -> true
                    TR.deckConfigInvalidParameters() -> true
                    else -> false
                }
            } catch (_: Throwable) {
                false
            }

        @VisibleForTesting
        fun Throwable.isDeckNotFoundInLimitsMapException(): Boolean =
            this is BackendInvalidInputException && message == "deck not found in limits map"
    }
}
