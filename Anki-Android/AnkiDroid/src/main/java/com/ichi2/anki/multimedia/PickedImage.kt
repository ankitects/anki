// SPDX-FileCopyrightText: 2026 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

/**
 * The result of an `ACTION_PICK` image chooser. The URI comes from whichever app the user picked,
 * so it's untrusted — a well-behaved picker returns a `content://`, and anything else (e.g. a
 * `file://`) is a bad or hostile picker we must not read.
 */
@JvmInline
value class PickedImage(
    private val intent: Intent,
) {
    /** The picked URI when it's a trusted `content://`, otherwise null. */
    val trustedUri: Uri?
        get() = intent.data?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
}
