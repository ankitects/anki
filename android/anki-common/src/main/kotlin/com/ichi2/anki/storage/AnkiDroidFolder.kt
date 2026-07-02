// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.storage

/**
 * Where AnkiDroid stores its collection.
 *
 * See https://github.com/ankidroid/Anki-Android/issues/5304 for more context.
 */
enum class AnkiDroidFolder {
    /**
     * The folder `~/AnkiDroid`, used by default.
     *
     * This folder is not deleted when the user uninstalls the app, which reduces the risk of data
     * loss, but increases the risk of space used on their storage when they don't want to.
     *
     * It cannot be used on the Play Store starting with SDK 30, as Google will not
     *  allow [android.Manifest.permission.MANAGE_EXTERNAL_STORAGE].
     */
    PUBLIC,

    /**
     * The app-private folder: `~/Android/data/com.ichi2.anki[.A]/files/AnkiDroid`.
     *
     * The user may delete it when they uninstall the app, risking data loss.
     * No permission dialog is required. This is the default on the Play Store
     */
    APP_PRIVATE,
}
