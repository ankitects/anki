// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

/** Permission to use a metered connection for one sync attempt. */
enum class MeteredSyncPermission {
    /** Apply the user's saved network preferences. */
    USE_PREFERENCES,

    /** Allow metered syncing for this attempt; the separate media restriction still applies. */
    ALLOW_METERED_SYNC_THIS_TIME,
}
