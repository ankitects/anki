// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import androidx.work.NetworkType
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.ShouldFetchMedia
import com.ichi2.utils.NetworkUtils.isActiveNetworkMetered

/**
 * Single source of truth for the "warn the user before syncing on a metered connection"
 * preference. All reads of [Prefs.allowSyncOnMeteredConnections] should go through here
 * so that the gating policy lives in one place.
 */
object MeteredSyncPolicy {
    /** True when sync should be blocked/prompted because the network is metered. */
    fun shouldBlock(): Boolean = getNetworkTypeRequiredForSync() == NetworkType.UNMETERED && isActiveNetworkMetered()

    /**
     * Capture the network restriction in queued work so WorkManager can enforce it if the
     * network changes before or during sync. Checking only the current connection is insufficient.
     */
    fun getNetworkTypeRequiredForSync(
        forMedia: Boolean = false,
        permission: MeteredSyncPermission = MeteredSyncPermission.USE_PREFERENCES,
    ): NetworkType =
        when {
            permission == MeteredSyncPermission.USE_PREFERENCES && !Prefs.allowSyncOnMeteredConnections -> NetworkType.UNMETERED
            forMedia && Prefs.shouldFetchMedia == ShouldFetchMedia.ONLY_UNMETERED -> NetworkType.UNMETERED
            else -> NetworkType.CONNECTED
        }

    /** Persist the user's "don't ask again" choice from the warning dialog. */
    fun setAlwaysAllow(allow: Boolean) {
        Prefs.allowSyncOnMeteredConnections = allow
    }

    /**
     * Run [onConfirm] immediately if the network is unmetered, metered-network sync is
     * allowed, or [skipPrompt] is set; otherwise ask the UI to retain a pending confirmation.
     *
     * @param skipPrompt `true` if the user has already accepted a metered sync earlier in this
     *   attempt (e.g. retry after conflict resolution); skips the warning.
     * @param onConfirmationRequired invoked when the UI must ask for permission before syncing
     * @param onConfirm receives the metered-network permission for this attempt
     */
    fun confirmThen(
        skipPrompt: Boolean = false,
        onConfirmationRequired: () -> Unit,
        onConfirm: (permission: MeteredSyncPermission) -> Unit,
    ) {
        if (skipPrompt) {
            onConfirm(MeteredSyncPermission.ALLOW_METERED_SYNC_THIS_TIME)
            return
        }
        if (!shouldBlock()) {
            onConfirm(MeteredSyncPermission.USE_PREFERENCES)
            return
        }
        onConfirmationRequired()
    }
}
