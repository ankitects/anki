// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.shareddeck

import androidx.lifecycle.ViewModel
import com.ichi2.anki.common.annotations.NeedsTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import kotlin.math.abs

/** State holder for [SharedDecksDownloadFragment]. */
class SharedDecksDownloadViewModel : ViewModel() {
    val uiState: StateFlow<SharedDecksDownloadUiState>
        field = MutableStateFlow(SharedDecksDownloadUiState())

    private val speedCalculator = DownloadSpeedCalculator()

    /** A download was enqueued. Also called on a retry, which clears the failed state. */
    fun onDownloadStarted(fileName: String) {
        Timber.i("download started")
        speedCalculator.reset()
        uiState.value = SharedDecksDownloadUiState(fileName = fileName)
    }

    fun onProgress(
        downloadedBytes: Long,
        totalBytes: Long,
        timeMillis: Long,
    ) {
        // DownloadManager reports -1 for a download whose size it does not know yet
        val percent = if (totalBytes > 0) abs(downloadedBytes * 100f / totalBytes) else 0f
        val speed = speedCalculator.update(downloadedBytes, timeMillis).toLong()
        val secondsRemaining = if (speed > 0 && totalBytes > 0) (totalBytes - downloadedBytes) / speed else null
        uiState.update {
            it.copy(
                percent = percent,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                speedBytesPerSecond = speed,
                secondsRemaining = secondsRemaining,
            )
        }
    }

    /** The [android.app.DownloadManager] query failed, so progress cannot be reported. */
    fun onProgressUnavailable() {
        uiState.update { it.copy(percent = null) }
    }

    fun onWaitingForNetwork(waiting: Boolean) {
        uiState.update { it.copy(isWaitingForNetwork = waiting) }
    }

    fun onDownloadComplete() {
        Timber.i("download complete")
        uiState.update { it.copy(phase = DownloadPhase.Complete, percent = 100f, secondsRemaining = 0) }
    }

    fun onDownloadFailed() {
        Timber.i("download failed")
        uiState.update { it.copy(phase = DownloadPhase.Failed, percent = 0f, speedBytesPerSecond = 0, secondsRemaining = null) }
    }

    /** The download hit AnkiWeb's download limit for a logged-out user, so a login is needed. */
    fun onLoginRequired() {
        Timber.i("login required to download more decks")
        uiState.update { it.copy(phase = DownloadPhase.LoginRequired) }
    }
}

/** Everything the download screen shows. */
data class SharedDecksDownloadUiState(
    val fileName: String? = null,
    /** `null` when progress could not be read, in which case the screen says only that a download is running. */
    val percent: Float? = 0f,
    val downloadedBytes: Long = 0,
    /** -1 until [android.app.DownloadManager] knows the size of the file. */
    val totalBytes: Long = -1,
    val speedBytesPerSecond: Long = 0,
    /** `null` until there is enough data to estimate it. */
    val secondsRemaining: Long? = null,
    val phase: DownloadPhase = DownloadPhase.Downloading,
    val isWaitingForNetwork: Boolean = false,
) {
    /** The download is running, but no bytes have arrived yet. */
    @NeedsTest("true only while Downloading with a known size and no bytes received")
    val isWaitingToStart: Boolean
        get() = phase == DownloadPhase.Downloading && percent != null && downloadedBytes <= 0
}

/** How far the download has got. Decides the status text and which buttons are offered. */
enum class DownloadPhase {
    /** Running, or paused waiting for the network. Cancel is offered. */
    Downloading,

    /** Finished. Import is offered. */
    Complete,

    /** Failed. Try again and Open in browser are offered. */
    Failed,

    /** The download hit AnkiWeb's limit for anonymous users. A login prompt is shown and the
     * screen returns to the shared decks WebView, where the user can log in. */
    LoginRequired,
}

/** Whole numbers for 0 and 100, one decimal place in between, so the common cases read cleanly. */
fun formatDownloadPercent(percent: Float): String {
    val whole = percent.toInt()
    return if (whole == 0 || whole == 100) whole.toString() else "%.1f".format(percent)
}
