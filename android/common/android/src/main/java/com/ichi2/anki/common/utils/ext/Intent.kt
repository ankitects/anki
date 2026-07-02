// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>

package com.ichi2.anki.common.utils.ext

import android.content.Intent

fun Intent.getLongExtra(key: String): Long? {
    @Suppress("DEPRECATION") // get()
    val value = extras?.get(key) ?: return null
    return value as Long
}
