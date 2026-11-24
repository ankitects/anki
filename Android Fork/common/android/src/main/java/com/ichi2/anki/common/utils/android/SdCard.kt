// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.common.utils.android

import android.os.Environment

/**
 * Utilities for accessing an SD Card (if the user's device supports one).
 *
 * The AnkiDroid collection folder can be stored on an external SD card. This was common
 * in older versions of Android, and is still supported.
 */
object SdCard {
    /**
     * Whether the device's primary external storage (the "SD card") is currently mounted
     * and writable.
     *
     * Returns false when storage is unmounted, removed, read-only, or in any other state
     * that prevents writes (see [Environment.getExternalStorageState]).
     */
    val isMounted: Boolean
        get() = Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()
}
