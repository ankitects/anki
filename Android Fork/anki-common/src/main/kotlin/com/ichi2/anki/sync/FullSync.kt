// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import com.ichi2.anki.libanki.Collection

/**
 * Whether a successful full collection sync should start a media sync (executed in the backend).
 *
 * Upstream [documents](https://github.com/ankitects/anki/blob/2727cf39b24869e9d379049c9aa141caaf66c49c/proto/anki/sync.proto#L85-L90)
 * that omitting `server_usn` skips media sync. When supplied, the backend
 * [starts media sync after a successful full sync](https://github.com/ankitects/anki/blob/2727cf39b24869e9d379049c9aa141caaf66c49c/rslib/src/backend/sync.rs#L452-L454).
 */
sealed interface MediaSync {
    /**
     * Leave media startup to the caller.
     *
     * Useful for separately enforcing network constraints.
     */
    data object Skip : MediaSync

    /** Start media immediately, using the server media USN returned by the initial collection sync. */
    data class Start(
        val serverUsn: Int,
    ) : MediaSync
}

/** Transfer the full collection to/from the server */
fun Collection.fullUploadOrDownload(
    auth: SyncAuth,
    upload: Boolean,
    mediaSync: MediaSync,
) = fullUploadOrDownload(
    auth = auth.toProto(),
    upload = upload,
    // The backend uses `usn != null` to start a media sync.
    // Make this distinction explicit, so a media sync is an explicit action.
    serverUsn =
        when (mediaSync) {
            MediaSync.Skip -> null
            is MediaSync.Start -> mediaSync.serverUsn
        },
)
