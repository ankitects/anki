// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <http://apps.ankiweb.net>

package com.ichi2.anki.libanki

fun Collection.getDeckConfigsForUpdateRaw(input: ByteArray): ByteArray = backend.getDeckConfigsForUpdateRaw(input)

fun Collection.updateDeckConfigsRaw(input: ByteArray): ByteArray = backend.updateDeckConfigsRaw(input)
