// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.sync

import anki.sync.SyncCollectionResponse
import anki.sync.SyncStatusResponse
import com.ichi2.anki.libanki.Collection
import net.ankiweb.rsdroid.Backend

/**
 * App representation of backend sync credentials.
 *
 * @see [anki.sync.SyncAuth]
 */
class SyncAuth(
    private val proto: anki.sync.SyncAuth,
) {
    val hkey: String
        get() = proto.hkey

    /** Null selects the default AnkiWeb server. */
    val endpoint: String?
        get() = proto.endpoint.takeIf { proto.hasEndpoint() }

    fun toProto(): anki.sync.SyncAuth = proto

    fun withEndpoint(endpoint: String): SyncAuth = SyncAuth(proto.toBuilder().setEndpoint(endpoint).build())
}

fun Backend.syncStatus(auth: SyncAuth): SyncStatusResponse = syncStatus(auth.toProto())

fun Backend.syncMedia(auth: SyncAuth) = syncMedia(input = auth.toProto())

fun Collection.syncCollection(
    auth: SyncAuth,
    syncMedia: Boolean,
): SyncCollectionResponse = syncCollection(auth.toProto(), syncMedia)
