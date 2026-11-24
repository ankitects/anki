// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import com.ichi2.anki.backupLock
import com.ichi2.anki.multiprofile.ProfileSwitchGuard.BlockReason
import com.ichi2.anki.multiprofile.ProfileSwitchGuard.SafetyCheck
import com.ichi2.anki.worker.mediaSyncLock
import com.ichi2.anki.worker.syncLock
import kotlinx.coroutines.sync.Mutex

/**
 * Blocks a switch while the collection is syncing in the background.
 *
 * Only the background sync needs this: a foreground sync holds a modal progress
 * dialog, so the user cannot reach the switch until it finishes.
 */
fun syncCheck() = lockCheck(syncLock, BlockReason.SYNC_IN_PROGRESS)

/** Blocks a switch while media is syncing. */
fun mediaSyncCheck() = lockCheck(mediaSyncLock, BlockReason.MEDIA_SYNC_IN_PROGRESS)

/** Blocks a switch while a background backup is running. */
fun backupCheck() = lockCheck(backupLock, BlockReason.BACKUP_IN_PROGRESS)

/**
 * Reads a lock that the work holds only while it runs, so work that is queued but
 * waiting, such as a sync waiting for the network, does not block the switch:
 * there is nothing to interrupt yet, and [safeRestartApp] holds the same lock, so the
 * work cannot start part way through the restart.
 *
 * [safeRestartApp] waits for work that is still running, so a check exists to tell the
 * user why the switch cannot happen yet rather than for safety.
 */
private fun lockCheck(
    lock: Mutex,
    reason: BlockReason,
) = SafetyCheck { reason.takeIf { lock.isLocked } }
