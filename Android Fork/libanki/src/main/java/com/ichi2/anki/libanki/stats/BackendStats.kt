// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>

package com.ichi2.anki.libanki.stats

import com.ichi2.anki.libanki.Collection

// These take and return bytes that the frontend TypeScript code will encode/decode.
fun Collection.cardStatsRaw(input: ByteArray): ByteArray = backend.cardStatsRaw(input)

fun Collection.graphsRaw(input: ByteArray): ByteArray = backend.graphsRaw(input)

fun Collection.getGraphPreferencesRaw(): ByteArray {
    val prefs =
        backend
            .getGraphPreferences()
            .toBuilder()
            .setBrowserLinksSupported(false)
            .build()
    return prefs.toByteArray()
}

fun Collection.setGraphPreferencesRaw(input: ByteArray): ByteArray = backend.setGraphPreferencesRaw(input)
