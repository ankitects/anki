// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.progress

import android.content.Context
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.dialogs.LoadingDialogFragment
import com.ichi2.anki.dialogs.showLoadingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.WeakHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Blocks touches and back, and shows/dismisses a loading dialog driven by [viewModel]'s progress flow.
 * [delayMillis] defers the initial show so quick operations don't flash a dialog.
 * [resolveMessage] maps the ViewModel's message to the text shown in the dialog.
 */
fun <M : Any> AnkiActivity.observeProgress(
    viewModel: HasProgress<M>,
    delayMillis: Duration = 600.milliseconds,
    resolveMessage: Context.(M) -> String,
) = observeProgressFor(this, viewModel, delayMillis, resolveMessage)

private fun <M : Any> AnkiActivity.observeProgressFor(
    owner: LifecycleOwner,
    viewModel: HasProgress<M>,
    delayMillis: Duration,
    resolveMessage: Context.(M) -> String,
) {
    val dialogTag = "${LoadingDialogFragment.TAG}:${viewModel.javaClass.name}"
    var holdingDialog = false
    var reshowDialog: (() -> Unit)? = null
    val dialogHolder: () -> Unit = { reshowDialog?.invoke() }
    val ownerLifecycle = owner.lifecycle
    var backBlock: OnBackPressedCallback? = null

    fun blockInput() {
        if (backBlock != null) return
        window.blockTouches()
        backBlock = onBackPressedDispatcher.addCallback(this) { Timber.i("Ignoring back while an operation runs") }
    }

    fun unblockInput() {
        val block = backBlock ?: return
        backBlock = null
        block.remove()
        window.unblockTouches()
    }

    fun holdDialog() {
        if (holdingDialog) return
        holdingDialog = true
        supportFragmentManager.addDialogHolder(dialogTag, dialogHolder)
    }

    fun releaseDialog() {
        if (!holdingDialog) return
        holdingDialog = false
        supportFragmentManager.removeDialogHolder(dialogTag, dialogHolder)
    }

    val restoringDialog =
        !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
            supportFragmentManager.findFragmentByTag(dialogTag) != null
    if (restoringDialog) holdDialog()

    val releaseIfOwnerStopped =
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && !ownerLifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) releaseDialog()
        }
    lifecycle.addObserver(releaseIfOwnerStopped)

    ownerLifecycle.coroutineScope.launch {
        try {
            ownerLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Pending "show after delay" job survives subsequent Active emissions so the
                // anti-flash delay isn't restarted on every progress update. Kept inside the
                // block: a job cancelled on stop must not linger into the next start.
                var pendingShow: Job? = null

                fun showDialogAfter(wait: Duration) =
                    ownerLifecycle.coroutineScope.launch(Dispatchers.Main) {
                        delay(wait)
                        val latest = viewModel.progressManager.progress.value
                        if (latest is ViewModelProgress.Active) {
                            holdDialog()
                            showLoadingDialog(
                                message = formatMessage(latest, resolveMessage),
                                cancellable = latest.cancellable,
                                tag = dialogTag,
                            )
                            if (latest.cancellable) wireCancelListener(viewModel, dialogTag)
                        }
                        pendingShow = null
                    }

                reshowDialog = {
                    pendingShow?.cancel()
                    pendingShow = showDialogAfter(Duration.ZERO)
                }
                val progress =
                    viewModel.progressManager.progress.onCompletion {
                        reshowDialog = null
                        pendingShow?.cancel()
                        unblockInput()
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) releaseDialog()
                    }
                progress.collect { state ->
                    when (state) {
                        is ViewModelProgress.Idle -> {
                            pendingShow?.cancel()
                            pendingShow = null
                            releaseDialog()
                            unblockInput()
                        }
                        is ViewModelProgress.Active -> {
                            blockInput()
                            if (holdingDialog) {
                                pendingShow?.cancel()
                                pendingShow = showDialogAfter(Duration.ZERO)
                            } else if (pendingShow == null) {
                                pendingShow = showDialogAfter(delayMillis)
                            }
                        }
                    }
                }
            }
        } finally {
            lifecycle.removeObserver(releaseIfOwnerStopped)
            releaseDialog()
        }
    }
}

private val dialogHolders = WeakHashMap<FragmentManager, MutableMap<String, MutableList<() -> Unit>>>()

private fun FragmentManager.addDialogHolder(
    tag: String,
    reshow: () -> Unit,
) {
    dialogHolders.getOrPut(this) { mutableMapOf() }.getOrPut(tag) { mutableListOf() }.add(reshow)
}

private fun FragmentManager.removeDialogHolder(
    tag: String,
    reshow: () -> Unit,
) {
    val holders = dialogHolders[this]?.get(tag) ?: return
    holders.remove(reshow)
    if (holders.isNotEmpty()) {
        holders.last().invoke()
        return
    }
    dialogHolders[this]?.remove(tag)
    (findFragmentByTag(tag) as? DialogFragment)?.dismissAllowingStateLoss()
}

private fun AnkiActivity.wireCancelListener(
    viewModel: HasProgress<*>,
    dialogTag: String,
) {
    supportFragmentManager.executePendingTransactions()
    val fragment =
        supportFragmentManager.findFragmentByTag(dialogTag)
            as? LoadingDialogFragment
    fragment?.dialog?.setOnCancelListener {
        viewModel.progressManager.requestCancel()
    }
}

/**
 * Follows this fragment's lifecycle, showing the dialog and blocking touches and back on the host activity.
 *
 * If the host is not an [AnkiActivity], logs a warning and returns — no observer is set up.
 *
 * @throws IllegalStateException if the fragment is not currently attached to an activity
 *   (propagated from [Fragment.requireActivity]).
 *
 * TODO: relax [showLoadingDialog]/[dismissLoadingDialog] to `FragmentActivity` and drop
 *   the cast.
 */
fun <M : Any> Fragment.observeProgress(
    viewModel: HasProgress<M>,
    delayMillis: Duration = 600.milliseconds,
    resolveMessage: Context.(M) -> String,
) {
    val activity = requireActivity() as? AnkiActivity
    if (activity == null) {
        Timber.w(
            "observeProgress called from a Fragment hosted by %s, which is not an AnkiActivity; skipping.",
            requireActivity().javaClass.simpleName,
        )
        return
    }
    activity.observeProgressFor(this, viewModel, delayMillis, resolveMessage)
}

private fun <M : Any> Context.formatMessage(
    state: ViewModelProgress.Active<M>,
    resolveMessage: Context.(M) -> String,
): String? {
    val text = state.message?.let { resolveMessage(it) }
    val amount = state.amount ?: return text
    val formattedAmount = state.formatAmount(amount)
    return when {
        text.isNullOrEmpty() -> formattedAmount
        else -> "$text${state.separator}$formattedAmount"
    }
}
